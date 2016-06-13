
package lupapalvelu.tiedonohjaus;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
 * <p>Java class for ElectronicNotificationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ElectronicNotificationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}AcceptionDate"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}AcceptionDescription"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}NotificationPeriod" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}Delivered" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}DeliveryDate" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}ArrivalDate" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ElectronicNotificationType", propOrder = {
    "acceptionDate",
    "acceptionDescription",
    "notificationPeriod",
    "delivered",
    "deliveryDate",
    "arrivalDate"
})
public class ElectronicNotificationType {

    @XmlElement(name = "AcceptionDate", required = true)
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar acceptionDate;
    @XmlElement(name = "AcceptionDescription", required = true)
    protected String acceptionDescription;
    @XmlElement(name = "NotificationPeriod")
    protected List<String> notificationPeriod;
    @XmlElement(name = "Delivered")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> delivered;
    @XmlElement(name = "DeliveryDate")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> deliveryDate;
    @XmlElement(name = "ArrivalDate")
    @XmlSchemaType(name = "date")
    protected List<XMLGregorianCalendar> arrivalDate;

    /**
     * Gets the value of the acceptionDate property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getAcceptionDate() {
        return acceptionDate;
    }

    /**
     * Sets the value of the acceptionDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setAcceptionDate(XMLGregorianCalendar value) {
        this.acceptionDate = value;
    }

    /**
     * Gets the value of the acceptionDescription property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAcceptionDescription() {
        return acceptionDescription;
    }

    /**
     * Sets the value of the acceptionDescription property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAcceptionDescription(String value) {
        this.acceptionDescription = value;
    }

    /**
     * Gets the value of the notificationPeriod property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the notificationPeriod property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getNotificationPeriod().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getNotificationPeriod() {
        if (notificationPeriod == null) {
            notificationPeriod = new ArrayList<String>();
        }
        return this.notificationPeriod;
    }

    /**
     * Gets the value of the delivered property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the delivered property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDelivered().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getDelivered() {
        if (delivered == null) {
            delivered = new ArrayList<XMLGregorianCalendar>();
        }
        return this.delivered;
    }

    /**
     * Gets the value of the deliveryDate property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the deliveryDate property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDeliveryDate().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getDeliveryDate() {
        if (deliveryDate == null) {
            deliveryDate = new ArrayList<XMLGregorianCalendar>();
        }
        return this.deliveryDate;
    }

    /**
     * Gets the value of the arrivalDate property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the arrivalDate property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getArrivalDate().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link XMLGregorianCalendar }
     * 
     * 
     */
    public List<XMLGregorianCalendar> getArrivalDate() {
        if (arrivalDate == null) {
            arrivalDate = new ArrayList<XMLGregorianCalendar>();
        }
        return this.arrivalDate;
    }

}
