package com.actvn.enotary;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ENotarySystemApplicationTests {

	@Test
	void applicationCanBeConstructed() {
		assertDoesNotThrow(ENotarySystemApplication::new);
	}

}
