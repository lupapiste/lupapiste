// Application handlers and organization handler roles.
LUPAPISTE.HandlerService = function() {
  "use strict";
  var self = this;
  var raw_roles =  [{ id: "first",
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
                            en: "IV Handler"}}];
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

  function indicateSaved() {
    hub.send( "indicator", {message: "saved", style: "positive"} );
  }

  self.nameAndRoleString = function( handler ) {
    var lastName  = util.getIn( handler, ["lastName"], "");
    var firstName = util.getIn( handler, ["firstName"], "");
    var role = _.get(self.findHandlerRole( handler.roleId), "name", "");
    return lastName && firstName && role
         ? sprintf( "%s %s (%s)", lastName, firstName, role )
         : "";
  };

  // Name is object (e.g., {fi: "Uusi nimi", sv: "Nytt namn", en: "New name"}).
  function upsertOrganizationRole( orgId, roleId, name ) {
    console.log( "Set name", orgId, roleId, name);
    indicateSaved();    
  }


  function processRawRole( orgId, role ) {
    var nameObs = ko.mapping.fromJS( role.name );
    ko.computed( function() {
      if( _.every( _.values( nameObs), _.flow( ko.unwrap, _.trim))
       && !ko.computedContext.isInitial()) {
        upsertOrganizationRole( orgId, role.id, ko.mapping.toJS( nameObs ));
      }
    });
    return _.defaults( {name: nameObs}, role);
  }

  var latestOrgId = null;
  var roles = ko.observableArray();
  
  self.organizationHandlerRoles = function( orgId ) {
    if( orgId && orgId !== latestOrgId ) {
      roles(_.map( raw_roles,
                   _.partial( processRawRole, orgId )));
      latestOrgId = orgId;
    }
    return roles;
  };

  self.organizationLanguages = function( orgId ) {
    return _.intersection( loc.getSupportedLanguages(),
                           _.keys( _.get( self.organizationHandlerRoles( orgId )(),
                                          "0.name")));
  };

  self.removeOrganizationHandlerRole = function( orgId, roleId ) {
    roles.remove( function( r ) {return r.id === roleId;});
  };

  self.addOrganizationHandlerRole = function( orgId ) {
    roles.push( processRawRole(orgId,
                               {id: _.uniqueId( TMP ),
                                name: _( self.organizationLanguages( orgId ))
                                      .map( function( lang ) {return [lang, ""];})
                                      .fromPairs()
                                      .value()}));    
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
        indicateSaved();
      }
    }
  }

  function removeHandler( handlerId ) {
    console.log( "AJAX: Remove handler:", handlerId);
    indicateSaved();
    
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
