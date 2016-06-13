package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ActionEvent")
public class ActionEvent {
    @XmlElement(name = "Description", required = true)
    protected String description;

    @XmlElement(name = "Type", required = true)
    protected String type;

    @XmlElement(name = "Function")
    protected String function;

    @XmlElement(name = "CorrectionReason")
    protected String correctionReason;

    @XmlElement(name = "Created", required = true)
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar created;

    @XmlElement(name = "Agent")
    protected List<AgentType> agent;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public void setCorrectionReason(String correctionReason) {
        this.correctionReason = correctionReason;
    }

    public XMLGregorianCalendar getCreated() {
        return created;
    }

    public void setCreated(XMLGregorianCalendar created) {
        this.created = created;
    }

    public List<AgentType> getAgent() {
        if (agent == null) {
            agent = new ArrayList<AgentType>();
        }
        return this.agent;
    }

    public void setAgent(List<AgentType> agent) {
        this.agent = agent;
    }
}
