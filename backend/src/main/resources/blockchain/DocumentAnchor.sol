// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

contract DocumentAnchor {
    struct Anchor {
        string requestId;
        string documentId;
        address sender;
        uint256 blockNumber;
        uint256 timestamp;
    }

    mapping(bytes32 => Anchor) private anchors;

    event DocumentAnchored(
        bytes32 indexed documentHash,
        string requestId,
        string documentId,
        address indexed sender
    );

    function registerDocument(bytes32 documentHash, string calldata requestId, string calldata documentId) external {
        require(documentHash != bytes32(0), "EMPTY_HASH");
        require(anchors[documentHash].timestamp == 0, "ALREADY_ANCHORED");

        anchors[documentHash] = Anchor({
            requestId: requestId,
            documentId: documentId,
            sender: msg.sender,
            blockNumber: block.number,
            timestamp: block.timestamp
        });

        emit DocumentAnchored(documentHash, requestId, documentId, msg.sender);
    }

    function isAnchored(bytes32 documentHash) external view returns (bool) {
        return anchors[documentHash].timestamp != 0;
    }

    function getAnchor(bytes32 documentHash) external view returns (
        string memory requestId,
        string memory documentId,
        address sender,
        uint256 blockNumber,
        uint256 timestamp
    ) {
        Anchor memory anchor = anchors[documentHash];
        return (anchor.requestId, anchor.documentId, anchor.sender, anchor.blockNumber, anchor.timestamp);
    }
}
