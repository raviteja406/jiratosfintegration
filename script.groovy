import com.atlassian.jira.component.ComponentAccessor
import org.apache.log4j.Category
import com.atlassian.jira.issue.Issue;
import org.apache.commons.io.IOUtils;
import com.atlassian.jira.util.io.InputStreamConsumer;
import com.atlassian.jira.util.PathUtils

// Get the current issue key
Issue issueKey = issue
def id = issueKey.getId()

// Get the current logged in user
def user = ComponentAccessor.getJiraAuthenticationContext().getUser().name

// Get a manager to handle the copying of attachments
def attachmentManager = ComponentAccessor.getAttachmentManager()

// Get the default attachment path from its manager
def attachmentPathManager = ComponentAccessor.getAttachmentPathManager().attachmentPath.toString()
def attachments = attachmentManager.getAttachments(issueKey) //attachments is list<attachments>

for (int i = 0; i < attachments.size(); i++) {
    String filePath = PathUtils.joinPaths(ComponentAccessor.getAttachmentPathManager().getDefaultAttachmentPath(), issueKey.getProjectObject().getKey(), issue.getKey(), attachments[i].getId().toString());
    File file = new File(filePath);
    if (file.exists()) {
        //return file.getText();
        FileOutputStream stream = new FileOutputStream(filePath);
        byte[] bytes;
        try {
            stream.write(bytes);
        } finally {
            stream.close();
        }
    }
}
