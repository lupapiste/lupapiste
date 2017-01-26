// Application handlers and organization handler roles.
LUPAPISTE.HandlerService = function() {
  "use strict";
  var self = this;
  var roles =  ko.observableArray([{ id: "first",
                                     name: {fi: "Käsittelijä",
                                            sv: "Handläggare",
                                            en: "Handler"},
                                     general: true},
                                   {id: "second",
                                    name: {fi: "KVV-käsittelijä",
                                           sv: "KVV-handläggare",
                                           en: "KVV Handler"}}]);

  self.organizationHandlerRoles = function( orgId ) {
    return ko.computed( function() {
      return roles();
    });    
  };

  self.removeOrganizationHandlerRole = function( orgId, roleId ) {
    roles( _.reject ( roles(), {id: roleId} ) );
  };

  self.addOrganizationHandlerRole = function( orgId ) {
    roles.push( {id: _.uniqueId( "role" ),
                 name: {fi: "", sv: "", en: ""}});    
  };

  // Name is object (e.g., {fi: "Uusi nimi"}).
  self.setOrganizationHandlerRole = function( orgId, roleId, name ) {
    console.log( "Set name", orgId, roleId, name );    
  };
  
};
