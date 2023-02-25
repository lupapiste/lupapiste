
package lupapalvelu.tiedonohjaus;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SecurityClassType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="SecurityClassType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Turvallisuusluokka I"/>
 *     &lt;enumeration value="Turvallisuusluokka II"/>
 *     &lt;enumeration value="Turvallisuusluokka III"/>
 *     &lt;enumeration value="Turvallisuusluokka IV"/>
 *     &lt;enumeration value="Ei turvallisuusluokiteltu"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "SecurityClassType")
@XmlEnum
public enum SecurityClassType {

    @XmlEnumValue("Turvallisuusluokka I")
    TURVALLISUUSLUOKKA_I("Turvallisuusluokka I"),
    @XmlEnumValue("Turvallisuusluokka II")
    TURVALLISUUSLUOKKA_II("Turvallisuusluokka II"),
    @XmlEnumValue("Turvallisuusluokka III")
    TURVALLISUUSLUOKKA_III("Turvallisuusluokka III"),
    @XmlEnumValue("Turvallisuusluokka IV")
    TURVALLISUUSLUOKKA_IV("Turvallisuusluokka IV"),
    @XmlEnumValue("Ei turvallisuusluokiteltu")
    EI_TURVALLISUUSLUOKITELTU("Ei turvallisuusluokiteltu");
    private final String value;

    SecurityClassType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static SecurityClassType fromValue(String v) {
        for (SecurityClassType c: SecurityClassType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
