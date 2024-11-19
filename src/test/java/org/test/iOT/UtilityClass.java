package org.test.iOT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

public class UtilityClass {

	public List<String> generateNonSyntheticData(String excelFilePath, String sheetName, String staticJsonFilePath, String jsonDirectory ) throws EncryptedDocumentException, IOException, InterruptedException {


		InputStream inputStreamTxt = new FileInputStream(staticJsonFilePath);
		generateAndWriteDataToExcel(excelFilePath, sheetName);

		List<Map<String, String>> entireData = getdataFromExcel(excelFilePath, sheetName);
		String jsonStaticData = readTextFile(inputStreamTxt);
		String copyStaticData = jsonStaticData;

		List<String> listOfJson = new ArrayList<String>();
		//String finalData = "";
		for (Map<String, String> map : entireData) {
			for (Entry<String, String> singleRowData : map.entrySet()) {
				jsonStaticData = jsonStaticData.replaceAll(singleRowData.getKey().trim(), singleRowData.getValue().trim());
			}
			listOfJson.add(jsonStaticData);
			//	finalData = finalData+jsonStaticData;
			jsonStaticData = copyStaticData;
		}
		// Get jsons file path
		writeTextToFile(listOfJson.toString(), jsonDirectory);
		//System.out.println(txtFilePath);
		return listOfJson;
	}

	private static void generateAndWriteDataToExcel(String filePath, String sheetName) throws IOException, InterruptedException{
		InputStream inputStream = new FileInputStream(filePath);
		Workbook workbook = WorkbookFactory.create(inputStream);
		Sheet sheet = workbook.getSheet(sheetName);
		DataFormatter df = new DataFormatter();
		FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

		int rowCount = sheet.getPhysicalNumberOfRows();
		int columnCount = sheet.getRow(0).getPhysicalNumberOfCells();

		int tatDateColumn = 0 ;
		for (int i = 0; i < columnCount; i++) {
			if (df.formatCellValue(sheet.getRow(0).getCell(i), evaluator).trim().equalsIgnoreCase("TAT_DATE")) {
				tatDateColumn = i;
				break;
			}
		}

		for (int i = 1; i < rowCount; i++) {

			int randomNumber = generateRandomNumber(100000);
			String tatDate = df.formatCellValue(sheet.getRow(i).getCell(tatDateColumn), evaluator).trim();
			String timeStamp = generateDateWithOffset(tatDate,"yyyyMMddhhmmssSSS");
			String date = generateDateWithOffset(tatDate,"dd-MM-yyyy");
			String messageId = "Msg_"+timeStamp;
			String lastUpdtedOrSynced = generateDateWithOffset(tatDate,"dd-MM-yyyy hh:mm:ss:SSS");
			String iotDeviceId = "IOT_"+randomNumber;
			String tempSensorId = "TEMP_"+randomNumber;

			for (int j = 0; j < columnCount; j++) {
				String cellData = df.formatCellValue(sheet.getRow(0).getCell(j), evaluator);

				if (cellData.equalsIgnoreCase("MESSAGE_ID_VAR")) {
					sheet.getRow(i).getCell(j).setCellValue(messageId);
				} else if(cellData.equalsIgnoreCase("IOT_DEVICE_ID_VAR")) {
					sheet.getRow(i).getCell(j).setCellValue(iotDeviceId);
				} else if(cellData.equalsIgnoreCase("SIMULATION_DATE")) {
					sheet.getRow(i).getCell(j).setCellValue(date);
				} else if(cellData.equalsIgnoreCase("TEMP_SENSOR_ID_VAR")) {
					sheet.getRow(i).getCell(j).setCellValue(tempSensorId);
				} else if(cellData.equalsIgnoreCase("LAST_UPDATED_VAR") || cellData.equalsIgnoreCase("LAST_SYNCED_VAR")) {
					sheet.getRow(i).getCell(j).setCellValue(lastUpdtedOrSynced);
				}
			}
			Thread.sleep(100);
		}
		OutputStream outputStream = new FileOutputStream(filePath);
		workbook.write(outputStream);
		workbook.close();
		inputStream.close();
		outputStream.close();
	}

	private static List<Map<String, String>> getdataFromExcel(String excelPath, String sheetName)
			throws EncryptedDocumentException, IOException {

		List<Map<String, String>> entireData = new LinkedList<Map<String, String>>();
		InputStream inputStream = new FileInputStream(excelPath);
		Workbook workbook = WorkbookFactory.create(inputStream);
		Sheet sheet = workbook.getSheet(sheetName);
		DataFormatter df = new DataFormatter();
		FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

		int rowCount = sheet.getLastRowNum();
		for (int i = 0; i < rowCount; i++) {
			Map<String, String> singleRowData = new LinkedHashMap<String, String>();
			int columnCount = sheet.getRow(i).getLastCellNum();
			for (int j = 0; j < columnCount; j++) {
				singleRowData.put(df.formatCellValue(sheet.getRow(0).getCell(j), evaluator), df.formatCellValue(sheet.getRow(i+1).getCell(j), evaluator));
			}
			entireData.add(singleRowData);
		}
		workbook.close();
		inputStream.close();
		return entireData;
	}

	private static String readTextFile(InputStream inputStream) throws IOException {
		StringBuilder content = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		while ((line = reader.readLine()) != null) {
			content.append(line).append("\n");
		}
		if (reader != null) {
			reader.close();
		}
		return content.toString();
	}

	private static String writeTextToFile(String data, String directory) throws IOException {
		// Generate fileName with time stamp
		String fileName = "IotData_"+new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss").format(new Date())+".txt";

		// Create the file in the specified directory
		File file = new File(directory, fileName);

		// Write data to the file
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write(data);
		if (writer != null) {
			writer.close();
		}
		return file.getAbsolutePath();
	}

	private static String generateDateWithOffset(String offset, String format) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.DAY_OF_MONTH, Integer.parseInt(offset));
		return new SimpleDateFormat(format).format(calendar.getTime());
	}

	private static int generateRandomNumber(int range) {
		return new Random().nextInt(range);
	}

	public Connection createConnection(String host,int port, String userName,String password) throws IOException, TimeoutException {
		ConnectionFactory    factory = new ConnectionFactory();
		Connection connection =null;
		factory.setHost(host);
		factory.setPort(port); 
		factory.setUsername(userName);
		factory.setPassword(password);
		connection= factory.newConnection();
		return connection;
	}
	public void produceMessage(Connection connection, String queueName,String msgToAdd) throws IOException, TimeoutException {
		Channel  channel = connection.createChannel();
		channel.queueDeclare(queueName, true, false, false, null);
		channel.basicPublish("", queueName, null, msgToAdd.getBytes());
		channel.close();
		//connection.close();
	}
	public void closeConnection(Connection connection) throws IOException, TimeoutException {
		connection.close();
	}
	public Session connectToSSH(String username, String host, String password, int port ) {
		Session session = null;
		try {
			//			JSch jsch = new JSch();
			//			// Create session
			//			session = jsch.getSession(username, host, port);
			//			session.setPassword(password);
			//
			//			// Set additional configurations, e.g., for accepting unknown host keys
			//			session.setConfig("StrictHostKeyChecking", "yes");
			//
			//			// Connect to the remote server
			//			session.connect();
		}catch (Exception e) {
			e.printStackTrace();
		}
		return session;
	}

	public void disconnectFromSSH(Session session) {
		//		session.disconnect();
	}

	public void checkMessage(Connection connection, String queueName,String msgToCheck ) throws IOException {
		Channel channel = connection.createChannel();


		GetResponse response = channel.basicGet(queueName, false); // Fetch a single message (non-auto-ack)
		if (response != null) {
			String message = new String(response.getBody(), "UTF-8");
			System.out.println("Message: " + message);
			if (message.contains(msgToCheck)) {
				System.out.println("Message with ID found!");
				// Acknowledge the message if desired
				channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
			} else {
				// Requeue the message
				channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
			}
		} else {
			System.out.println("No messages in the queue.");
		}
	}

	public void fileTransfer(Session session, String localFilePath, String remoteFilePath){
		try {
			// Create SFTP channel
			ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();
			// Upload the file
			channelSftp.put(new FileInputStream(localFilePath), remoteFilePath);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
