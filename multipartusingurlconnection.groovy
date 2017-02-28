import com.atlassian.jira.component.ComponentAccessor
import org.apache.log4j.Category
import com.atlassian.jira.issue.Issue;
import org.apache.commons.io.IOUtils;
import com.atlassian.jira.util.io.InputStreamConsumer;
import com.atlassian.jira.util.PathUtils
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import org.apache.commons.codec.binary.Base64;
import java.net.*;
import groovy.json.JsonSlurper;

def user = "JIRA_API";                                                                // SNOW UserID
def passwd = "5h1n30N4ever!";                                                        // SNOW Password

//def URLParam = "https://sfsf.service-now.com/api/now/table/incident/838a52f6db4d3e408e5ef9231d9619cc";
def URLParam = "https://sfsf.service-now.com/api/now/attachment/file?table_name=incident&table_sys_id=838a52f6db4d3e408e5ef9231d9619cc&file_name=Issue_screenshot.jpg"
//def URLParam ="https://instance.service-now.com/api/now/attachment/file?table_name=incident&table_sys_id=d71f7935c0a8016700802b64c67c11c6&file_name=Issue_screenshot.jpg"
URLConnection connection;

// Get the current issue key
Issue issueKey = issue
def id = issueKey.getId()

// Get a manager to handle the copying of attachments
def attachmentManager = ComponentAccessor.getAttachmentManager()

// Get the default attachment path from its manager
def attachmentPathManager = ComponentAccessor.getAttachmentPathManager().attachmentPath.toString()
def attachments = attachmentManager.getAttachments(issueKey) //attachments is list<attachments>
BufferedInputStream buf;
int size = 0;
byte[] bytes = new byte[size];
StringBuilder parameters;
DataOutputStream wr;

for (int i = 0; i < attachments.size(); i++) {
    String filePath = PathUtils.joinPaths(ComponentAccessor.getAttachmentPathManager().getDefaultAttachmentPath(), issueKey.getProjectObject().getKey(), issue.getKey(), attachments[i].getId().toString());
    File file = new File(filePath);
    if (file.exists()) {
        buf = new BufferedInputStream(new FileInputStream(file));
        size = (int) file.length();
        bytes = new byte[size];
        buf.read(bytes, 0, bytes.length);
        buf.close();
        parameters = new StringBuilder();
        parameters.append("file1=");
        parameters.append(URLEncoder.encode(new String(bytes), "UTF-8"));

        URL url;
        url = new URL(URLParam);
        def authString = user + ":" + passwd;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "image/jpeg");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Length", Integer.toString(parameters.toString().getBytes().length));

        wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(parameters.toString());
        wr.flush();
        wr.close();
        connection.connect();
    }
}
if (connection.getResponseCode() == 201) {
    String nextLine;
    InputStreamReader inStream = new InputStreamReader(connection.getInputStream());
    BufferedReader buff = new BufferedReader(inStream);
    def slurper = new JsonSlurper()
    def result = slurper.parseText(buff.readLine())
    return result.result.number
} else {
    return connection.getResponseMessage();
}
