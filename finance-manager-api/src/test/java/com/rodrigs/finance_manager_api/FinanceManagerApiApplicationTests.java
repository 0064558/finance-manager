package com.rodrigs.finance_manager_api;

import com.rodrigs.finance_manager_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

@ActiveProfiles("test")
@SpringBootTest
class FinanceManagerApiApplicationTests extends PostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
