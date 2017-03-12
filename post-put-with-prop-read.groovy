import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder
import com.atlassian.jira.issue.util.IssueChangeHolder
import com.opensymphony.workflow.InvalidInputException
import org.apache.commons.codec.binary.Base64;
import java.net.*;
import groovy.json.JsonSlurper;
import groovy.json.JsonBuilder;

/*MS: Please remove redundant imports*/
/*RT: Fixed: All the redundant fields are already removed as the code*/

init();

def init() {

    sync_with_snow();

}

/* <<<These API signatures will change>>>
*	create_snow_incident(): This API is responsible for establishing connection
*							between JIRA and SNOW and create an incident in SNOW
*							with the details of JIRA ticket.
*/

def sync_with_snow() {

    //Reading from properties file
    Properties props = new Properties()
    File propsFile = new File('/data/JIRA6/atlassian-jira-6.4.12/conf/jirasnowconfig.properties')
    props.load(propsFile.newDataInputStream())

    def requestMethod
    def URLParam = props.getProperty('incidenturlparam');

    if (!isIncidentCreated()) {
        requestMethod = "POST";
    } else {
        requestMethod = "PUT"
        def customFieldManager = ComponentAccessor.getCustomFieldManager()
        def cf_snow_sys_id = customFieldManager.getCustomFieldObject("customfield_18983");
        URLParam = URLParam + "/" + issue.getCustomFieldValue(cf_snow_sys_id)
    }
    def query = "{\"short_description\":\"Test post request from JIRA INTEGRATION TEST\"}";  //!< Data to POST to SNOW

    def user = props.getProperty('user');
    def passwd = props.getProperty('passwd');

    URLConnection connection;                                                   //!< URL Connection object
    def issue_id = issue;
    try {
        URL url;
        url = new URL(URLParam);
        def authString = user + ":" + passwd;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        connection = url.openConnection();
        connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        connection.setRequestMethod(requestMethod);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.doOutput = true
        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write(build_req(issue_id));
        writer.flush()
        writer.close()
        connection.connect()
    } catch (Exception e) {
        //ToDo:
        /*MS: Let's look at writing it to a log file. Jira installations use Log4J and we should be able to access
            and utilize it for writing any failure conditions */
    }
    if ((connection.getResponseCode().toString() == props.getProperty('CREATED')) ||
            (connection.getResponseCode().toString() == props.getProperty('OK'))) {

        String nextLine;
        InputStreamReader inStream = new InputStreamReader(connection.getInputStream());
        BufferedReader buff = new BufferedReader(inStream);

        // Extracting details of SNOW response
        def slurper = new JsonSlurper()
        def result = slurper.parseText(buff.readLine())
        setCustomFields(result, issue, props)
        //return connection.getResponseMessage(); /*MS: The calling function should do something with this return*/
    } else {

        //return connection.getResponseMessage();  /*MS: The calling function should do something with this return*/
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

def isIncidentCreated() {
    boolean incCreated = false;
    def snow_sys_id = "customfield_18983";

    def customFieldManager = ComponentAccessor.getCustomFieldManager()
    def cf_snow_sys_id = customFieldManager.getCustomFieldObject(snow_sys_id);

    if (issue.getCustomFieldValue(cf_snow_sys_id)) {
        return true;
    } else {
        return false;
    }
}