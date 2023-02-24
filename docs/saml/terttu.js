
/**
 * User Profile
 */
var profile = {
  userName: "terttu.panaani@pori.fi",
  nameIdFormat: "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
  givenname: "Terttu",
  surname: "Panaani",
  emailaddress: "terttu.panaani@pori.fi",
  groups: "GG_Lupapiste_RAVA_read, GG_Lupapiste_RAVA_Arkistonhoitaja"
};

/**
 * SAML Attribute Metadata
 */
var metadata = [
  {
    id: "givenname",
    optional: true,
    displayName: "First Name",
    description: "The given name of the user",
    multiValue: false
  },
  {
    id: "surname",
    optional: true,
    displayName: "Last Name",
    description: "The surname of the user",
    multiValue: false
  },
  {
    id: "emailaddress",
    optional: false,
    displayName: "E-Mail Address",
    description: "The e-mail address of the user",
    multiValue: false
  },
  {
    id: "mobilePhone",
    optional: true,
    displayName: "Mobile Phone",
    description: "The mobile phone of the user",
    multiValue: false
  },
  {
    id: "groups",
    optional: true,
    displayName: "Groups",
    description: "Group memberships of the user",
    multiValue: true
  }];

module.exports = {
  user: profile,
  metadata: metadata
};
