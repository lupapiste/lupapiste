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

  var TMP = "temporary-";

  self.authorities = ko.observableArray();

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

  self.nameAndRoleString = function( handler ) {
    var lastName  = util.getIn( handler, ["lastName"], "");
    var firstName = util.getIn( handler, ["firstName"], "");
    var role = _.get(self.findHandlerRole( handler.roleId), "name", "");
    return lastName && firstName && role
         ? sprintf( "%s %s (%s)", lastName, firstName, role )
         : "";
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

  function updateHandler( handlerId, data, raw ) {
    raw = raw || {};
    var h = _.find( self.applicationHandlers(), {id: handlerId});
    if( h ) {
      console.log( "Update handler:", handlerId, data );
      var auth = _.find( self.authorities(), {id: data.userId });
      if( auth ) {
        h.firstName(_.get(auth, "firstName", "" ));
        h.lastName( _.get(auth, "lastName", "" ));
      }
      if( data.userId
       && data.roleId
       && (data.userId !== raw.userId || data.roleId !== raw.roleId)) {
        console.log( "AJAX: Upsert handler:", handlerId, data );
      }
    }
  }

  function removeHandler( handlerId ) {
    console.log( "AJAX: Remove handler:", handlerId);
  }

  function processRawHandler( raw ) {
    var m =  _.defaults( ko.mapping.fromJS( _.pick( raw,
                                                    ["roleId", "userId",
                                                     "firstName", "lastName"])),
                         raw);
    ko.computed( function() {
      var data = {userId: m.userId(),
                  roleId: m.roleId()};
      if( !ko.computedContext.isInitial()) {
        updateHandler( raw.id, data, raw );
      }
    });
    return m;
  }

  self.applicationHandlers = ko.observableArray( _.map( raw_handlers,
                                                        processRawHandler ));
  
  self.newApplicationHandler = function() {
    self.applicationHandlers.push(processRawHandler( {id: _.uniqueId( TMP ),
                                                      roleId: ko.observable(),
                                                      userId: ko.observable(),
                                                      lastName: ko.observable(""),
                                                      firstName: ko.observable("")}));
  };

  self.removeApplicationHandler = function( handlerId ) {
    self.applicationHandlers.remove( function( h ) {
      return h.id === handlerId;
    });
    if( !_.startsWith( handlerId, TMP )) {
      removeHandler( handlerId );
    }
  };

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
