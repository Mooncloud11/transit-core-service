package com.predictivetransit.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * BackendApplicationTests: Basic integration test to ensure that the 
 * Spring application context loads successfully.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:backendtestdb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=update"
})
class BackendApplicationTests {

	/**
	 * Test to verify if the application context can be started without errors.
	 */
	@Test
	void contextLoads() {
	}
}
