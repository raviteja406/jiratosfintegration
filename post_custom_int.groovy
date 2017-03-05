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

init();

def init() {
    if (!isIncidentCreated())
        create_snow_incident();
}

/* <<<These API signatures will change>>>
*	create_snow_incident(): This API is responsible for establishing connection
*							between JIRA and SNOW and create an incident in SNOW
*							with the details of JIRA ticket.
*/

def create_snow_incident() {

    def user = "JIRA_API";                                                        //!< SNOW UserID
    def passwd = "5h1n30N4ever!";                                               //!< SNOW Password
    def requestMethod = "POST";                                                 //!< Method Type
    def URLParam = "https://sfsf.service-now.com/api/now/table/incident";       //!< SNOW Incident table URL
    def query = "{\"short_description\":\"Test post request from JIRA INT\"}";  //!< Data to POST to SNOW
    
    /*MS: Please read all these Properties from a .Properties file. One suggestion is to use Groovy ConfigSlurper library*/

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
    if (connection.getResponseCode() == 201) {
        String nextLine;
        InputStreamReader inStream = new InputStreamReader(connection.getInputStream());
        BufferedReader buff = new BufferedReader(inStream);

        // Extracting details of SNOW response
        def slurper = new JsonSlurper()
        def result = slurper.parseText(buff.readLine())
        setCustomFields(result, issue)
        return connection.getResponseMessage(); /*MS: The calling function should do something with this return*/
    } else {
        return connection.getResponseMessage();  /*MS: The calling function should do something with this return*/
    }
}

/* <<<These API signatures will change>>>
*	Description:	
*	setCustomFields(def result, def issue): This API is responsible for populating custom fields in JIRA
*											once the ticket is created in SNOW and details are fetched from SNOW
*	parameters:	[IN] result: This will be JSON response from SNOW
*					 issue: This Will be issue key for which response has to be updated.
*/

def setCustomFields(def result, def issue) {

    IssueChangeHolder changeHolder = new DefaultIssueChangeHolder();

    def snow_inc_id = "customfield_18981";
    def snow_etask_id = "customfield_18982";
    def snow_sys_id = "customfield_18983";
    def snow_url = "customfield_18984";
    
    /*MS: I confirmed that following deep-link URL does work: 
    https://sfsf.service-now.com/incident.do?sys_id=f629752adbb076408e5ef9231d961978
    Concatenate: def SNIncidentURL = URL + 'incident.do?sys_id= + Sys_Id) 
    Sys_Id returned from SN in above format and SET(SNIncidentURL ) to a custom URL field in JIRA. 
    If User is already logged in the click takes him/her to the Incident detail page else to the login page.
    */
    
    def snow_test_url = "https://sfsf.service-now.com/nav_to.do?uri=%2Fincident.do%3Fsys_id%3D838a52f6db4d3e408e5ef9231d9619cc%26sysparm_stack%3D%26sysparm_view%3D";
    
    /*MS: Please read all these Properties from a .Properties file. One suggestion is to use Groovy ConfigSlurper library*/

    def customFieldManager = ComponentAccessor.getCustomFieldManager()

    def cf_snow_inc_id = customFieldManager.getCustomFieldObject(snow_inc_id);
    def cf_snow_etask_id = customFieldManager.getCustomFieldObject(snow_etask_id);
    def cf_snow_sys_id = customFieldManager.getCustomFieldObject(snow_sys_id);
    def cf_snow_url = customFieldManager.getCustomFieldObject(snow_url);

    cf_snow_inc_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_inc_id), result.result.number), changeHolder);
    cf_snow_etask_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_etask_id), "ETASK123"), changeHolder);
    cf_snow_sys_id.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_sys_id), result.result.sys_id), changeHolder);
    cf_snow_url.updateValue(null, issue, new ModifiedValue(issue.getCustomFieldValue(cf_snow_url), snow_test_url), changeHolder);
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
