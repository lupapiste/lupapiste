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
                                           en: "KVV Handler"}},
                                   {id: "third",
                                    name: {fi: "IV-käsittelijä",
                                           sv: "IV-handläggare",
                                           en: "IV Handler"}}]);
  var raw_handlers = [{id: "foo",
                       roleId: "first",
                       firstName: "First",
                       lastName: "Last",
                       userId: "1233"},
                      {id: "bar",
                       roleId: "second",
                       firstName: "First lakdfjaldkfa",
                       lastName: "Last aldskfjalsdfj",
                       userId: "1233"}];
  
  self.authorities = ko.observableArray();

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

  self.nameAndRoleString = function( handler ) {
    return sprintf( "%s %s (%s)",
                    util.getIn( handler, ["lastName"], ""),
                    util.getIn( handler, ["firstName"], ""),
                    _.get(self.findHandlerRole( handler.roleId), "name", ""));
  };

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
    var h = _.find( self.applicationHandlers(), {id: handlerId});
    var auth = _.find( self.authorities(), {id: data.userId });
    h.firstName( auth.firstName );
    h.lastName( auth.lastName);    
  };

  self.applicationHandlers = ko.observableArray( _.map( raw_handlers, function( h ) {
    var m =  _.defaults( ko.mapping.fromJS( _.pick( h,
                                                    ["roleId", "userId",
                                                    "firstName", "lastName"])),
                         h);
    ko.computed( function() {
      if( m.roleId() && m.userId() && !ko.computedContext.isInitial()) {
        updateApplicationHandler( h.id, {userId: m.userId(),
                                         roleId: m.roleId()} );
      }
    });
    return m;
  }));

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
