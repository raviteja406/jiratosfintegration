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
import com.atlassian.jira.issue.label.LabelManager
import org.springframework.util.StringUtils
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


init();

def init() {

    //Reading from properties file    
    Properties props = new Properties()
    File propsFile = new File('/data/JIRA6/atlassian-jira-6.4.12/conf/jirasnowconfig.properties')
    props.load(propsFile.newDataInputStream())

    def log = Logger.getLogger(props.getProperty('LOG_FILE_PATH'))
    log.setLevel(Level.ERROR)
    
    def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()
	def changeItems = changeHistoryManager.getAllChangeItems(issue)
   
	sync_with_snow(props, getActionType())
}

/* <<<These API signatures will change>>>
*	create_snow_incident(): This API is responsible for establishing connection
*							between JIRA and SNOW and create an incident in SNOW
*							with the details of JIRA ticket.
*/

def sync_with_snow(def props, def action) {

    def URLParam = props.getProperty('sn_etask_url')
    def user = props.getProperty('user');
    def passwd = props.getProperty('passwd');

    URLConnection connection;
    def issue_id = issue;
    try {
        URL url;
        url = new URL(URLParam);
        log.error "url" + url
        def authString = user + ":" + passwd;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Basic " + authStringEnc);

        if (action == "SRCH") {
            log.error "action - " + action
            connection.setRequestMethod("GET");
        } else {
            log.error "action - " + action
            connection.setRequestMethod("POST");
        }

        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.doOutput = true
        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write(build_req(issue_id,action));
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
        log.error "Response  - " + result 
        
        if (action == "CRT") {
            log.error "action setting field  - " + action 
            setCustomFields(result, issue, props,action)
			sendAttachments(props, result)
        }
		
        if (action == "SRCH") {
            log.error "action setting field  - " + action 
            setCustomFields(result, issue, props,action)
            sendAttachments(props, result)
            sync_with_snow(props, "UPD")            
        }
        //return connection.getResponseCode().toString()
    }else {
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

def setCustomFields(def result, def issue, def props,def action) {

    log.error "setcustom fields"
    IssueChangeHolder changeHolder = new DefaultIssueChangeHolder();
    labelManager = ComponentManager.getComponentInstanceOfType(LabelManager.class)

    def snow_etask_number = "customfield_18982";
	def snow_etask_sys_id = "customfield_19286";

    def customFieldManager = ComponentAccessor.getCustomFieldManager()
    def labelSet_inc_number = new HashSet()
	def labelSet_inc_sys_id = new HashSet()

    if( action == "SRCH"){	//SRCH gets a different response
    	for(int i=0; i < result.incidents.size(); i++){
     		labelSet_inc_number.add(result.incidents.number[i])
			labelSet_inc_sys_id.add(result.incidents.sys_id[i])
    	}		
    }
    
    if( action == "CRT"){    	
		labelSet_inc_number.add(result.inc_number)
		labelSet_inc_sys_id.add(result.inc_sys_id)
    }

    def componentManager = ComponentManager.getInstance()
    def authContext = componentManager.getJiraAuthenticationContext()
    def user = authContext.getUser()

    //Add label
    labelManager.setLabels(user.getDirectoryUser(), issue.id, 19180, labelSet_inc_number, false, false)
    labelManager.setLabels(user.getDirectoryUser(), issue.id, 19287, labelSet_inc_sys_id, false, false)

    def cf_snow_etask_number = customFieldManager.getCustomFieldObject(snow_etask_number);
	def cf_snow_etask_sys_id = customFieldManager.getCustomFieldObject(snow_etask_sys_id);
    cf_snow_etask_number.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_etask_number), result.number), changeHolder);
	cf_snow_etask_sys_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_etask_sys_id), result.sys_id), changeHolder);
}

/* <<<These API signatures will change>>>
*	Description:
*	setCustomFields(def result, def issue): This API is responsible for populating custom fields in JIRA
*											once the ticket is created in SNOW and details are fetched from SNOW
*	parameters:	[IN] result: This will be JSON response from SNOW
*					 issue: This Will be issue key for which response has to be updated.
*/

def build_req(def issueKey,def action) {

    def customFieldManager = ComponentAccessor.getCustomFieldManager()

    def cf_etp_tkt_priority = customFieldManager.getCustomFieldObject("customfield_10002");
    def etp_priority = issue.getCustomFieldValue(cf_etp_tkt_priority)     

    labelManager = ComponentManager.getComponentInstanceOfType(LabelManager.class)
    def labelSet = labelManager.getLabels(issue.id, 19180)	//19180 customefiled id for SN incident

    try{
    	def val_assigned_to = issue.getAssignee().getDisplayName();	//displayname exception for unassigned cases
    }catch(Exception e){            	
        val_assigned_to = "Unassigned"
    }
    
    def json = new JsonBuilder()
    def root = json jira_id: issueKey.getKey(), 
        			description: issueKey.getDescription(), 
        			ticket_priority: etp_priority.toString(), 
        			ActionType: action,
        			u_type: issue.getIssueTypeObject().getName(),
        			state: issue.getStatusObject().getName(),
        			summary: issueKey.getSummary(), 
        			project: issue.projectObject.getKey(),
        			assigned_to: val_assigned_to,
        			inc_number: labelSet.collect {it.label}
    
    log.error "request message - " + json.toString()
    return json.toString()
}

def getActionType() {
    def action_type
    def sn_etask_sys_id = "customfield_19286" //servicenow etask sys id

    labelManager = ComponentManager.getComponentInstanceOfType(LabelManager.class)
    def labelSet = labelManager.getLabels(issue.id, 19180)

    def customFieldManager = ComponentAccessor.getCustomFieldManager()
    def cf_sn_etask_sys_id = customFieldManager.getCustomFieldObject(sn_etask_sys_id)

    if ((issue.getCustomFieldValue(cf_sn_etask_sys_id) == null) && (labelSet.size() == 0)) {
        action_type = "CRT"
    } else if ((issue.getCustomFieldValue(cf_sn_etask_sys_id) == null) && (labelSet.size() != 0)) {
        action_type = "SRCH"
    } else if ((issue.getCustomFieldValue(cf_sn_etask_sys_id) != null) && (labelSet.size() != 0)) {
        action_type = "UPD"
    } else
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
                URLParam = props.getProperty('sn_attachment_url')
                URLParam = URLParam + response.sys_id + "&file_name=" + attachments[i].getFilename()
                log.error "URL - " + URLParam
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

            if ((connection.getResponseCode().toString() != props.getProperty('CREATED'))) {
                log.error "response on attachments " + connection.getResponseCode()
            }
            else{
                log.error "response on attachments " + connection.getResponseCode()
            }
        }
    }
}