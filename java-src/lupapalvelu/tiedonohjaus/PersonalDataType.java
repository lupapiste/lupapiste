
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PersonalDataType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PersonalDataType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ei sisällä henkilötietoja"/>
 *     &lt;enumeration value="sisältää henkilötietoja"/>
 *     &lt;enumeration value="sisältää arkaluontoisia henkilötietoja"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "PersonalDataType")
@XmlEnum
public enum PersonalDataType {

    @XmlEnumValue("ei sis\u00e4ll\u00e4 henkil\u00f6tietoja")
    EI_SISÄLLÄ_HENKILÖTIETOJA("ei sis\u00e4ll\u00e4 henkil\u00f6tietoja"),
    @XmlEnumValue("sis\u00e4lt\u00e4\u00e4 henkil\u00f6tietoja")
    SISÄLTÄÄ_HENKILÖTIETOJA("sis\u00e4lt\u00e4\u00e4 henkil\u00f6tietoja"),
    @XmlEnumValue("sis\u00e4lt\u00e4\u00e4 arkaluontoisia henkil\u00f6tietoja")
    SISÄLTÄÄ_ARKALUONTOISIA_HENKILÖTIETOJA("sis\u00e4lt\u00e4\u00e4 arkaluontoisia henkil\u00f6tietoja");
    private final String value;

    PersonalDataType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PersonalDataType fromValue(String v) {
        for (PersonalDataType c: PersonalDataType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
