package org.test.iOT;

import java.util.List;

import org.testng.annotations.Test;

import com.jcraft.jsch.Session;
import com.rabbitmq.client.Connection;

public class PushDataAMQP {
	static String host="49.249.29.5";
	static	int port=8104;
	static String userName="admin";
	static String password="admin";
	static Connection connection =null;
	static String queueName="Fireflink";

	@Test
	public void dataSimulationToRabbitMQ() throws Exception {

		String excelFilePath = ".\\DataFiles\\SimulationData.xlsx";
		String sheetName = "SimulationData";
		String staticJsonFilePath = ".\\DataFiles\\StaticJsonFile.txt";
		String jsonDirectory = ".\\DataFiles\\";

		UtilityClass utilityClass = new UtilityClass();
		try {

			Session session = utilityClass.connectToSSH("chidori", "1234", password, 22);
			List<String> jsonList = utilityClass.generateNonSyntheticData(excelFilePath, sheetName, staticJsonFilePath, jsonDirectory);
			connection=utilityClass.createConnection(host, port, userName, password);
			for(int i=0;i<jsonList.size();i++) {
				utilityClass.produceMessage(connection, queueName, jsonList.get(i));	
				utilityClass.checkMessage(connection, queueName,jsonList.get(i) );
			}

			utilityClass.disconnectFromSSH(session);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			utilityClass.closeConnection(connection);
		}
	}
}
