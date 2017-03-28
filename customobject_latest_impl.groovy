import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder
import com.atlassian.jira.issue.util.IssueChangeHolder
import com.opensymphony.workflow.InvalidInputException
import org.apache.commons.codec.binary.Base64;
import java.net.*;
import groovy.json.JsonSlurper;
import groovy.json.JsonBuilder;
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.apache.log4j.Category
import org.apache.commons.io.IOUtils;
import com.atlassian.jira.util.io.InputStreamConsumer;
import com.atlassian.jira.util.PathUtils
import java.nio.charset.StandardCharsets

init();

def init() {

    //Reading from properties file
    Properties props = new Properties()
    File propsFile = new File('/data/JIRA6/atlassian-jira-6.4.12/conf/jirasnowconfig.properties')
    props.load(propsFile.newDataInputStream())

    def log = Logger.getLogger(props.getProperty('LOG_FILE_PATH'))
    log.setLevel(Level.ERROR)

	sync_with_snow(props, getActionType())
}

/* <<<These API signatures will change>>>
*	create_snow_incident(): This API is responsible for establishing connection
*							between JIRA and SNOW and create an incident in SNOW
*							with the details of JIRA ticket.
*/

def sync_with_snow(def props, def action) {

    def URLParam = props.getProperty('incidenturlparam');   
    def user = props.getProperty('user');
    def passwd = props.getProperty('passwd');
	
    URLConnection connection;
    def issue_id = issue;
    try {
        URL url;
        url = new URL(URLParam);
        def authString = user + ":" + passwd;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
		
        if( action == "SRCH")
        	connection.setRequestMethod("GET");
        else
            connection.setRequestMethod("POST");
        
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.doOutput = true
        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write(build_req(issue_id));
        writer.flush()
        writer.close()
        connection.connect()
    } catch (Exception e) {
        log.error("JIRA-SNOW: ERROR: Unable to connect:" + e)
    }
    if ((connection.getResponseCode().toString() == props.getProperty('OK'))) {

        String nextLine;
        InputStreamReader inStream = new InputStreamReader(connection.getInputStream());
        BufferedReader buff = new BufferedReader(inStream);

        // Extracting details of SNOW response
        def slurper = new JsonSlurper()
        def result = slurper.parseText(buff.readLine())

        sendAttachments(props, result)
        setCustomFields(result, issue, props)
        //return connection.getResponseCode().toString()
    } else {
        log.error("JIRA-SNOW: ERROR: Response:" + connection.getResponseMessage())
        //return connection.getResponseCode().toString()
    }
}

/* <<<These API signatures will change>>>
*	Description:
*	setCustomFields(def result, def issue): This API is responsible for populating custom fields in JIRA
*											once the ticket is created in SNOW and details are fetched from SNOW
*	parameters:	[IN] result: This will be JSON response from SNOW
*					 issue: This Will be issue key for which response has to be updated.
*/

def setCustomFields(def result, def issue, def props) {

    IssueChangeHolder changeHolder = new DefaultIssueChangeHolder();

    def snow_inc_id = "customfield_18981";
    def snow_etask_id = "customfield_18982";
    def snow_sys_id = "customfield_18983";
    def snow_url = "customfield_18984";

    def snow_base_url = props.getProperty('snow_base_url')
    def incident_key = props.getProperty('deep_url_inc_key')
    def sys_id = result.result.sys_id;
    def snow_inc_url = "$snow_base_url$incident_key$sys_id".toString()

    def customFieldManager = ComponentAccessor.getCustomFieldManager()

    def cf_snow_inc_id = customFieldManager.getCustomFieldObject(snow_inc_id);
    def cf_snow_etask_id = customFieldManager.getCustomFieldObject(snow_etask_id);
    def cf_snow_sys_id = customFieldManager.getCustomFieldObject(snow_sys_id);
    def cf_snow_url = customFieldManager.getCustomFieldObject(snow_url);

    cf_snow_inc_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_inc_id), result.result.number), changeHolder);
    cf_snow_etask_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_etask_id), "ETASK123"), changeHolder);
    cf_snow_sys_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_sys_id), result.result.sys_id), changeHolder);
    cf_snow_url.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_url), snow_inc_url), changeHolder);
}

/* <<<These API signatures will change>>>
*	Description:
*	setCustomFields(def result, def issue): This API is responsible for populating custom fields in JIRA
*											once the ticket is created in SNOW and details are fetched from SNOW
*	parameters:	[IN] result: This will be JSON response from SNOW
*					 issue: This Will be issue key for which response has to be updated.
*/

def build_req(def issueKey) {
    def priority = issueKey.getPriorityObject().getName()
    def description = issueKey.getDescription();

    def json = new JsonBuilder()
    //def root = json jiraid: issueKey.getKey(), priority: priority, description: description
    def root = json description: description
    return json.toString()
}

def getActionType() {
    def action_type
    def sn_etask_sys_id = "customfield_18982"
	def sn_inc_id = "customfield_18981"

    def customFieldManager = ComponentAccessor.getCustomFieldManager()
    def cf_sn_etask_sys_id = customFieldManager.getCustomFieldObject(sn_etask_sys_id)
	def cf_sn_inc_id = customFieldManager.getCustomFieldObject(sn_inc_id)
	
    if ( ( issue.getCustomFieldValue(cf_sn_etask_sys_id) == null ) && ( issue.getCustomFieldValue(cf_sn_inc_id) == null ) ) {
        action_type = "CRT"
    } else if( ( issue.getCustomFieldValue(cf_sn_etask_sys_id) == null ) ) {
        action_type = "SRCH"
    }else if(( issue.getCustomFieldValue(cf_sn_etask_sys_id) != null ) && ( issue.getCustomFieldValue(cf_sn_inc_id) != null )){
		action_type = "UPD"
	}
	else
		action_type = "INVALID"
	
	return action_type
}

def sendAttachments(def props, def response) {

    def passwd = props.getProperty('passwd');
    def user = props.getProperty('user');
    log.error "sending attachments"
    // Get the current issue key
    Issue issueKey = issue
    def id = issueKey.getId()

    // Get a manager to handle the copying of attachments
    def attachmentManager = ComponentAccessor.getAttachmentManager()

    // Get the default attachment path from its manager
    def attachmentPathManager = ComponentAccessor.getAttachmentPathManager().attachmentPath.toString()
    def attachments = attachmentManager.getAttachments(issueKey) //attachments is list<attachments>

    FileInputStream fis;
    int size = 0;
    byte[] buffer;
    def encodeddata; //byte array
    def str_encodeddata; //string
    DataOutputStream wr;

    for (int i = 0; i < attachments.size(); i++) {
        String filePath = PathUtils.joinPaths(ComponentAccessor.getAttachmentPathManager().getDefaultAttachmentPath(), issueKey.getProjectObject().getKey(), issue.getKey(), attachments[i].getId().toString());
        File file = new File(filePath);

        if (file.exists()) {
            try {
                URL url;
                URLParam = props.getProperty('attachmenturlparam')
                URLParam = URLParam + response.result.sys_id + "&file_name=" + attachments[i].getFilename()

                url = new URL(URLParam);
                def authString = user + ":" + passwd;
                byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
                String authStringEnc = new String(authEncBytes);
                connection = url.openConnection();
                connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setDoInput(true);

                def mimeType = connection.guessContentTypeFromName(attachments[i].getFilename());

                connection.setRequestProperty("Content-Type", mimeType);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Length", Integer.toString(str_encodeddata.toString().getBytes().length));

                buffer = new byte[file.length()];
                fis = new FileInputStream(file);

                int read;
                while ((read = fis.read(buffer)) != -1) {
                    connection.getOutputStream().write(buffer, 0, read);
                }

                encodeddata = Base64.encodeBase64(buffer);
                str_encodeddata = new String(buffer);
                fis.close();

                wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(str_encodeddata);
                wr.flush();
                wr.close();
                connection.connect();
            } catch (Exception e) {
                log.error "Exception while send" + e
            }
        }
        if (connection.getResponseCode() != props.getProperty('CREATED')) {
            log.error "Error response from server for attachment" + connection.getResponseMessage()
        }
    }
}