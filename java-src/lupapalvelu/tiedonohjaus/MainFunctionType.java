
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MainFunctionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MainFunctionType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}Title"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}FunctionCode"/>
 *         &lt;element ref="{http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1}FunctionClassification"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MainFunctionType", propOrder = {
    "title",
    "functionCode",
    "functionClassification"
})
public class MainFunctionType {

    @XmlElement(name = "Title", required = true)
    protected String title;
    @XmlElement(name = "FunctionCode", required = true)
    protected String functionCode;
    @XmlElement(name = "FunctionClassification", required = true)
    protected FunctionClassificationType functionClassification;

    /**
     * SÄHKE2 Metatietomalli: 6.3.5 Nimeke (ClassificationScheme.Title)
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the value of the title property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * Gets the value of the functionCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFunctionCode() {
        return functionCode;
    }

    /**
     * Sets the value of the functionCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFunctionCode(String value) {
        this.functionCode = value;
    }

    /**
     * Gets the value of the functionClassification property.
     * 
     * @return
     *     possible object is
     *     {@link FunctionClassificationType }
     *     
     */
    public FunctionClassificationType getFunctionClassification() {
        return functionClassification;
    }

    /**
     * Sets the value of the functionClassification property.
     * 
     * @param value
     *     allowed object is
     *     {@link FunctionClassificationType }
     *     
     */
    public void setFunctionClassification(FunctionClassificationType value) {
        this.functionClassification = value;
    }

}
