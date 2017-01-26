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
  var handlers = ko.observableArray( [{id: "foobar",
                                       roleId: "first",
                                       firstName: "First",
                                       lastName: "Last",
                                       userId: "1233"},
                                      {id: "foobar",
                                       roleId: "second",
                                       firstName: "First lakdfjaldkfa",
                                       lastName: "Last aldskfjalsdfj",
                                       userId: "1233"}]);
  console.log( "Handlers:", handlers );
  self.authorities = ko.observableArray();

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

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

  self.getApplicationHandlerRoles = ko.computed( function() {
    return _.map( roles(), function( role ) {
      return {id: role.id, name: {fi: role.name.fi}, general: role.general};
    });
  });

  self.getApplicationHandlers = ko.pureComputed( function() {
    var m = _.reduce( self.getApplicationHandlerRoles(),
                    function( acc, role ) {
                      return _.set( acc, role.id, role );
                    }, {});

    return _.map( handlers(), function( h ) {
      return _.set( h,
                    "text",
                    sprintf( "%s %s (%s)",
                           _.get( h, "lastName", ""),
                           _.get( h, "firstName", ""),
                             _.get( m, [h.roleId, "name", loc.getCurrentLanguage()], "")));
    });
  });

  hub.subscribe( "contextService::enter",
                 function( data ) {
                   ajax.query( "application-authorities", {id: data.applicationId})
                   .success( function( res ) {
                     self.authorities( res.authorities );
                   })
                   .call();
                 });

  hub.subscribe( "contextService::leave", function() {
    self.authorities.removeAll();
  });
};
