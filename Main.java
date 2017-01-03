import java.net.*;
import java.io.*;
import java.util.Date;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Iterator;
import java.io.File;

class Main {
	public static void main(String[] args) throws Exception {

		// Listen for a connection from a client
		ServerSocket serverSocket = new ServerSocket(1234);
		if(Desktop.isDesktopSupported())
			Desktop.getDesktop().browse(new URI("http://localhost:1234"));
		else
			System.out.println("Please direct your browser to http://localhost:1234.");
		while(true)
		{
			Socket clientSocket = serverSocket.accept();
			System.out.println("Got a connection!");
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

			// Receive the request from the client
			String inputLine;
			String url = "";
			String post = "";
			boolean isGet = false;
			int postContentLength = 0;
			StringBuilder postRequest = new StringBuilder();
			
			while ((inputLine = in.readLine()) != null) {
				System.out.println("The client said: " + inputLine);
				if(inputLine.contains("GET ")) {
					int end = inputLine.indexOf(" HTTP");
					url = inputLine.substring(4, end);
					isGet = true;
				}
				if(inputLine.contains("POST ")) {
					int end = inputLine.indexOf(" HTTP");
				}
				if(inputLine.contains("Content-Length: ")) {
					String sub = inputLine.substring(16, inputLine.length());
					postContentLength = Integer.parseInt(sub);
				}
 				if(inputLine.length() < 2){
					while (postRequest.length() < postContentLength) {
						postRequest.append((char) in.read());
					}
 					break;
 				}
			}
			

			String dateString = (new Date()).toGMTString();
			
			// Determine if POST or GET
			String payload = "";
			if (url.length() > 0) {
				payload = generatePage();
			}
			else {
				payload = generatePostReturn(postRequest.toString());
			}
			
			// Send HTTP headers
			System.out.println("Sending a response...");
			out.print("HTTP/1.1 200 OK\r\n");
			out.print("Content-Type: text/html\r\n");
			out.print("Content-Length: " + Integer.toString(payload.length()) + "\r\n");
			out.print("Date: " + dateString + "\r\n");
			out.print("Last-Modified: " + dateString + "\r\n");
			out.print("Connection: close\r\n");
			out.print("\r\n");

			// Send the payload
			out.println(payload);
			clientSocket.close();
		}
	}
	
	// build response to POST request
	static String generatePostReturn(String postRequest) {
		System.out.println("received from post request: " + postRequest);
		
		// parse request
		String currentDirectory = "";
		if (postRequest.contains("curDirectory:")) {
			int start = postRequest.indexOf("curDirectory:")+13;
			int end = getEndOfElement(postRequest, start);
			currentDirectory = postRequest.substring(start, end);
		}
		String requestDirectory = "";
		if (postRequest.contains("requestedFilepath:")) {
			int start = postRequest.indexOf("requestedFilepath:")+18;
			int end = getEndOfElement(postRequest, start);
			requestDirectory = postRequest.substring(start, end);
		}
		
		// determine request
		String sendBack = "";
		String current = "";
		
		// back one
		if (requestDirectory.startsWith("..")) {
			File parent = new File(currentDirectory);
			parent = parent.getParentFile();
			current = parent.getPath();
			sendBack = sendBackFiles(parent);
		}
		// to folder
		if (requestDirectory.startsWith("index.html?cd=")) {
			File goTo = new File(requestDirectory.substring(14, requestDirectory.length()));
			current = goTo.getPath();
			sendBack = sendBackFiles(goTo);
		}
		// display file contents
		if (requestDirectory.startsWith("openFile=")) {
			String toOpen = requestDirectory.substring(9, requestDirectory.length());
			return readFile(toOpen);
			
		}
		
		sendBack = (sendBack + "{currentDirectory:" + current + "}");
		System.out.println("sendback is " + sendBack);
		return sendBack;
	}
	
	static String readFile(String f) {
		StringBuilder payload = new StringBuilder();
		try {
			String currentLine;
			BufferedReader br = new BufferedReader(new FileReader(f));
			while ((currentLine = br.readLine()) != null)
				payload.append(currentLine + "<br>");
		} catch (IOException e) {
			e.printStackTrace();
			}
		return payload.toString();
	}
	
	static String sendBackFiles(File filePath) {
		System.out.println(filePath.getPath());
		
		LinkedList<File> listOfFiles = getFiles(filePath);
		LinkedList<File> documents = getDocuments(listOfFiles);
		LinkedList<File> directories = getDirectories(listOfFiles);
		
		StringBuilder sendBack = new StringBuilder();
		sendBack.append("{documents:[");
		Iterator<File> itDoc = documents.iterator();
			while (itDoc.hasNext()){
				File f = itDoc.next();
				sendBack.append(f.getName() + ":" + f);
				if (itDoc.hasNext())
					sendBack.append(",");
			}
		sendBack.append("]}{directories:[");
		Iterator<File> itDir = directories.iterator();
			while (itDir.hasNext()){
				File f = itDir.next();
				sendBack.append(f.getName() + ":" + f);
				if (itDir.hasNext())
					sendBack.append(",");
			}
		sendBack.append("]}");
		return sendBack.toString();
	}
	
	static int getEndOfElement(String bigString, int start) {
		int end = start;
		while (bigString.charAt(end) != ',' && bigString.charAt(end) != '}') {
			end++;
		}
		return end;
	}
	
	// build page for GETs
	static String generatePage() {
		String url = System.getProperty("user.dir");
		File currentLocation = new File(url);
		LinkedList<File> listOfFiles = getFiles(currentLocation);
		LinkedList<File> documents = getDocuments(listOfFiles);
		LinkedList<File> directories = getDirectories(listOfFiles);
	
		StringBuilder payload = new StringBuilder();
		
		payload.append ("<html><head>\n" +
		"<script type=\"text/javascript\">\n" +
		"\n" +
		"function httpPost(url, payload, callback) {\n" +
			"var request = new XMLHttpRequest();\n" +
			"request.onreadystatechange = function() {\n" +
				"if(request.readyState == 4) {\n" +
					"if(request.status == 200)\n" +
					"	callback(request.responseText);\n" +
					"else\n" +
					"{\n" +
					"	if(request.status == 0 && request.statusText.length == 0)\n" +
						"	alert(\"Request blocked by same-origin policy\");\n" +
					"	else\n" +
						"	alert(\"Server returned status \" + request.status +\n" +
						"	\", \" + request.statusText);\n" +
					"}\n" +
				"}\n" +
			"};\n" +
			"request.open('post', url, true);\n" +
			"request.setRequestHeader('Content-Type',\n" +
				"'application/x-www-form-urlencoded');\n" +
			"request.send(payload);\n" +
		"}\n" +
		"\n" +
		"function cb(response) {\n" +
			"if (response.startsWith(\"{\")) {\n" +
			"	updateFolders(response);\n" +
			"	updateFiles(response);\n" +
			"	updateCurDirectory(response);\n" +
			"	updateText(\" \")\n" +
			"}\n" +
			"else\n" +
			"	updateText(response);\n" +
		"}\n" +
		"\n" +
		"function updateText(response) {\n" +
			"document.getElementById(\"text\").innerHTML = response;\n" +
		"}\n" +
		"\n" +
		"function updateFiles(response) {\n" +
			"var files = parseFiles(response);\n" +
			"fileList.options.length=0;\n" +
			"for (var i = 0; i < files.length; i++) {\n" +
			"	var semi = files[i].indexOf(':');\n" +
			"	var fileName = files[i].substring(0,semi);\n" +
			"	var filePath = (\"openFile=\" + files[i].substring(semi+1));\n" +
			"	fileList.options[i]=new Option(fileName, filePath, false, false);\n" +
			"}\n" +
		"}\n" +
		"\n" +
		"function updateFolders(response) {\n" +
			"var folders = parseFolders(response);\n" +
			"folderList.options.length=1;\n" +
			"for (var i = 0; i < folders.length; i++) {\n" +
			"	var semi = folders[i].indexOf(':');\n" +
			"	var folderName = folders[i].substring(0,semi);\n" +
			"	var folderPath = (\"index.html?cd=\" + folders[i].substring(semi+1));\n" +
			"	folderList.options[i+1]=new Option(folderName, folderPath, false, false);\n" +
			"}\n" +
		"}\n" +
		"\n" +
		"function updateCurDirectory(response) {\n" +
			"var curDirectory = parseCurrentDirectory(response)\n" +
			"document.getElementById(\"currentDirectory\").innerHTML = curDirectory;\n" +
		"}\n" +
		"\n" +
		"function parseCurrentDirectory(response) {\n" +
		"	var start = response.lastIndexOf(':');\n" +
		"	var end = response.lastIndexOf('}');\n" +
		"	var curDirectory = response.substring(start+1, end);\n" +
		"	return curDirectory;\n" +
		"}\n" +
		"\n" +
		"function parseFiles(response) {\n" +
		"	var start = response.indexOf('[');\n" +
		"	var end = response.indexOf(']');\n" +
		"	var files = response.substring(start+1, end);\n" +
		"	files = files.split(',');\n" +
		"	return files;\n" +
		"}\n" +
		"\n" +
		"function parseFolders(response) {\n" +
		"	var start = response.lastIndexOf('[');\n" +
		"	var end = response.lastIndexOf(']');\n" +
		"	var folders = response.substring(start+1, end);\n" +
		"	folders = folders.split(',');\n" +
		"	return folders;\n" +
		"}\n" +
		"\n" +
		"function makeSendPayload(msg) {\n" +
		"	var curDirectory = document.getElementById(\"currentDirectory\").innerHTML;\n" +
		"	payload = \"{curDirectory:\" + curDirectory + \",requestedFilepath:\" + msg + \"}\";\n" +
		"	httpPost(\"http://localhost:1234\", payload, cb);\n" +
		"}\n" +
		"\n" +
		"</script>\n" +
		"</head><body>\n");
		payload.append("<table>\n" +
		"<tr><td align=right>Current directory:</td><td id=\"currentDirectory\">\n");
		payload.append(url);
		payload.append("<tr><td>\n" +
		"<b>Folders:</b><br>\n" +
		"<select id=\"folderList\" size=\"15\" style=\"width: 280px\" ondblclick=\"makeSendPayload(this.value)\">\n" +
			"<option value=\"..\">..</option>\n");
			
			Iterator<File> itDir = directories.iterator();
			while (itDir.hasNext()){
				File f = itDir.next();
				payload.append("<option value=\"index.html?cd=" + f + "\">" + f.getName() + "</option> \n");
			}
			
		payload.append("</select>\n" +
		"</td><td>\n" +
		"<b>Files:</b><br>\n" +
		"<select id=\"fileList\" size=\"15\" style=\"width: 280px\" ondblclick=\"makeSendPayload(this.value)\">\n");
			
			Iterator<File> itDoc = documents.iterator();
			while (itDoc.hasNext()){
				File f = itDoc.next();
				payload.append("<option value=\"openFile=" + url + "/" + f.getName() + "\">" + f.getName() + "</option> \n");
			}
			
		payload.append("</select>\n" +
		"</td></tr></table>\n" +
		"<p id=text></p>\n" +
		"</body></html>\n");
		
		return payload.toString();
	}
	
	static LinkedList<File> getFiles(File fileName) {
		
		File listOfFiles[] = fileName.listFiles();
		if (listOfFiles == null)
			System.out.println("empty set of files");
		
		LinkedList<File> fileLL = new LinkedList<File>();
		for (File file : listOfFiles) {
			fileLL.add(file);
		}
	
		return fileLL;
	}

	static LinkedList<File> getDocuments(LinkedList<File> listOfFiles) {
		LinkedList<File> documents = new LinkedList<File>();
		Iterator<File> itDoc = listOfFiles.iterator();
		while (itDoc.hasNext()){
			File f = itDoc.next();
			if (f.isFile()) {
				documents.add(f);
			}
		}
		return documents;
	}
	
	static LinkedList<File> getDirectories(LinkedList<File> listOfFiles) {
		LinkedList<File> directories = new LinkedList<File>();
		Iterator<File> itDir = listOfFiles.iterator();
		while (itDir.hasNext()){
			File f = itDir.next();
			if (f.isDirectory()) {
				directories.add(f);
			}
		}
		return directories;
	}
}