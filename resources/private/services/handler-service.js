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
  var handlers = ko.observableArray( [{id: "foo",
                                       roleId: "first",
                                       firstName: "First",
                                       lastName: "Last",
                                       userId: "1233"},
                                      {id: "bar",
                                       roleId: "second",
                                       firstName: "First lakdfjaldkfa",
                                       lastName: "Last aldskfjalsdfj",
                                       userId: "1233"}]);
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

  self.applicationHandlerRoles = ko.computed( function() {
    return _.map( roles(), function( role ) {
      return {id: role.id,
              name: _.get( role, ["name", loc.getCurrentLanguage()], ""),
              general: role.general};
    });
  });

  self.findHandlerRole = function( roleId ) {
    return _.find( self.applicationHandlerRoles(),
                 {id: ko.unwrap( roleId )});
  };

  function updateApplicationHandler( handlerId, data ) {
    console.log( "Upsert handler:", handlerId, data );
  }

  self.applicationHandlers = ko.pureComputed( function() {
    var m = _.reduce( self.applicationHandlerRoles(),
                    function( acc, role ) {
                      return _.set( acc, role.id, role );
                    }, {});

    return _.map( handlers(), function( h ) {
      var roleId = ko.observable( h.roleId);
      var userId = ko.observable( h.userId);
      ko.computed( function() {
        if( h.roleId !== roleId() || h.userId !== userId()) {
          updateApplicationHandler( h.id, {userId: userId(),
                                           roleId: roleId()} );
        }
      });
      return _.merge( {},
                      h,
                      {text: sprintf( "%s %s (%s)",
                                      _.get( h, "lastName", ""),
                                      _.get( h, "firstName", ""),
                                      _.get( m, [h.roleId, "name"], "")),
                      roleId: roleId,
                      userId: userId});
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
