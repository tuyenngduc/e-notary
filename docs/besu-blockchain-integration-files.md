# Giải Thích Các File Kết Nối Besu Blockchain

Tài liệu này giải thích các file/class được thêm để kết nối backend e-notary với mạng Hyperledger Besu có transaction `HYBRID_PQ` và chữ ký hậu lượng tử.

## 1. Cấu Hình

### `backend/src/main/resources/application.yml`

Thêm nhóm cấu hình:

```yaml
app:
  blockchain:
```

Nhóm này dùng để cấu hình endpoint Besu, chain id, private key gửi giao dịch, địa chỉ smart contract, đường dẫn khóa PQ và JAR ký hậu lượng tử.

Các biến môi trường quan trọng:

- `BESU_RPC_URL`: RPC node Besu, mặc định `http://localhost:8545`.
- `BESU_CHAIN_ID`: chain id của mạng Besu, mặc định `1337`.
- `BESU_SENDER_PRIVATE_KEY`: private key ECDSA của tài khoản gửi transaction.
- `BESU_DOCUMENT_ANCHOR_CONTRACT`: địa chỉ smart contract `DocumentAnchor`.
- `BESU_PQ_PRIVATE_KEY_PATH`: file private key hậu lượng tử.
- `BESU_PQ_PUBLIC_KEY_PATH`: file public key hậu lượng tử.
- `BESU_PQ_SIGNER_JAR`: JAR dùng để ký PQ, nằm trong `../besu/pq-tests/pq-signer/build/libs/`.

## 2. Smart Contract

### `backend/src/main/resources/blockchain/DocumentAnchor.sol`

Smart contract lưu bằng chứng hash của văn bản công chứng.

Chức năng chính:

- `registerDocument(bytes32 documentHash, string requestId, string documentId)`: ghi hash văn bản lên blockchain.
- `isAnchored(bytes32 documentHash)`: kiểm tra hash đã tồn tại trên blockchain chưa.
- `getAnchor(bytes32 documentHash)`: lấy thông tin request/document đã ghi.
- Event `DocumentAnchored`: phát ra khi một văn bản được ghi nhận.

Backend không lưu nội dung file lên blockchain, chỉ lưu SHA-256 hash của văn bản đã ký cuối cùng.

## 3. Các Class Trong `com.actvn.enotary.blockchain`

### `BlockchainProperties`

Map cấu hình từ `app.blockchain.*` trong `application.yml` vào object Java.

Class này giúp các service khác lấy cấu hình Besu/PQ theo kiểu type-safe thay vì đọc trực tiếp từng biến môi trường.

### `BesuJsonRpcClient`

Client gọi JSON-RPC tới Besu.

Dùng cho các method:

- `eth_getTransactionCount`: lấy nonce của sender.
- `eth_sendRawTransaction`: gửi raw transaction HYBRID_PQ.
- `eth_getTransactionReceipt`: chờ transaction được mine.
- `eth_blockNumber`, `eth_chainId`, `net_peerCount`: hiển thị thông tin admin blockchain.
- `eth_call`: kiểm tra hash trên smart contract khi xác minh tài liệu.

### `JsonRpcException`

Exception riêng cho lỗi khi gọi Besu JSON-RPC.

Dùng để phân biệt lỗi RPC với lỗi nghiệp vụ khác trong hệ thống.

### `HexUtils`

Helper xử lý hex:

- bỏ tiền tố `0x`.
- chuyển hex thành byte array.
- chuyển byte array thành hex có tiền tố `0x`.

Class này tránh lặp code xử lý hex trong encoder, contract helper và signer.

### `RlpEncoder`

Encoder RLP tối thiểu để tự động tạo raw transaction Ethereum/Besu.

Lý do cần class này: transaction `HYBRID_PQ` type `0x05` là format tùy biến của Besu PQ branch, nên backend phải tự encode raw transaction thay vì dùng transaction ECDSA thông thường.

### `PqSignerClient`

Wrapper gọi tool PQ signer JAR.

Nhiệm vụ:

- gọi `java -jar <pq-signer.jar> sign <private-key> <txHash>` để tạo `pqSignature`.
- gọi `java -jar <pq-signer.jar> get-public-key <public-key>` để lấy `pqPublicKey`.
- kiểm tra file JAR/key có tồn tại trước khi ký.

Backend hiện tại không tự implement Dilithium/Falcon, mà tái sử dụng tool có sẵn trong `../besu/pq-tests`.

### `HybridPqTransactionEncoder`

Tạo transaction Besu HYBRID_PQ type `0x05`.

Luôn ký bằng 2 lớp:

- ECDSA: ký transaction hash bằng `BESU_SENDER_PRIVATE_KEY`.
- PQ: ký cùng transaction hash bằng `PqSignerClient`.

Sau đó class này RLP encode các field:

- chain id
- nonce
- fee
- gas limit
- contract address
- calldata
- access list rỗng
- chữ ký ECDSA `yParity/r/s`
- `pqSignature`
- `pqPublicKey`

Kết quả trả về là raw transaction hex để gửi bằng `eth_sendRawTransaction`.

### `DocumentAnchorContract`

Helper tạo calldata gọi smart contract `DocumentAnchor`.

Chức năng:

- tạo calldata cho `registerDocument(bytes32,string,string)`.
- tạo calldata cho `isAnchored(bytes32)`.
- decode kết quả boolean trả về từ `eth_call`.

Class này giúp backend gọi contract mà không cần sinh wrapper Solidity/Web3j đầy đủ.

## 4. Service Chính

### `backend/src/main/java/com/actvn/enotary/service/BlockchainService.java`

Đây là điểm tích hợp chính vào nghiệp vụ e-notary.

Luồng ghi blockchain:

1. `VideoSessionService.signDocument(...)` tạo/cập nhật file PDF đã ký.
2. Khi cả người dân và công chứng viên đã ký, service gọi `blockchainService.anchorSignedDocument(...)`.
3. `BlockchainService` kiểm tra document đã có transaction chưa.
4. Nếu chưa có:
   - lấy nonce từ Besu.
   - tạo calldata `registerDocument`.
   - encode và ký transaction HYBRID_PQ.
   - gửi transaction lên Besu.
   - chờ receipt.
   - lưu transaction hash, block number, chain id vào bảng `blockchain_transactions`.

Luồng xác minh:

1. API public upload file cần xác minh.
2. `BlockchainService` tính SHA-256 của file.
3. Tìm hash trong bảng `blockchain_transactions`.
4. Gọi `eth_call isAnchored(hash)` để đối chiếu hash có tồn tại on-chain.
5. Trả về `VERIFIED` nếu DB và blockchain cùng xác nhận.

## 5. Test

### `backend/src/test/java/com/actvn/enotary/service/BlockchainServiceTest.java`

Test đảm bảo:

- Nếu document chưa có transaction, service sẽ tạo giao dịch Besu và lưu kết quả receipt vào DB.
- Nếu document đã có transaction, service không gửi lại transaction nữa.

Test dùng mock cho Besu RPC và encoder, không cần chạy node Besu thật.

## 6. Lưu Ý Khi Chạy Thật

Cần đảm bảo:

- Besu PQ network đang chạy, RPC mở tại `BESU_RPC_URL`.
- Smart contract `DocumentAnchor` đã deploy và địa chỉ được gán vào `BESU_DOCUMENT_ANCHOR_CONTRACT`.
- Tài khoản `BESU_SENDER_PRIVATE_KEY` có balance trên chain.
- `pq-signer` JAR đã build.
- File PQ private/public key tồn tại và đúng cùng thuật toán với Besu PQ branch.
- Backend chạy bằng JDK 21.

