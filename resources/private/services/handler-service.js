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
                            en: "IV Handler"}},
                   {id: "disabled",
                    name: {fi: "Gone-käsittelijä",
                           sv: "Gone-handläggare",
                           en: "Gone Handler"},
                    disabled: true}];

  // var raw_handlers = [{id: "foo",
  //                      roleId: "first",
  //                      firstName: "Ronja",
  //                      lastName: "Sibbo",
  //                      userId: "777777777777777777000024",
  //                      roleName: "Käsittelijä"},
  //                     {id: "bar",
  //                      roleId: "second",
  //                      firstName: "First",
  //                      lastName: "Last",
  //                      userId: "1234",
  //                      roleName: "KVV-käsittelijä"},
  //                     {id: "baz",
  //                      roleId: "disabled",
  //                      firstName: "Ronja",
  //                      lastName: "Sibbo",
  //                      userId: "777777777777777777000024",
  //                      roleName: "Gone-käsittelijä"}];

  var TMP = "temporary-";

  
  self.authorities = ko.observableArray();
  self.applicationHandlers = ko.observableArray();

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

  function allowed( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  }

  self.canEdit = function() {
    return allowed( "application-authorities");
  };

  function indicateSaved() {
    hub.send( "indicator", {message: "saved", style: "positive"} );
  }

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
    return _.map( self.organizationHandlerRoles( lupapisteApp.models.application.organization())(),
                  function( role ) {
                    return _.defaults( {name: _.get( role, ["name", loc.getCurrentLanguage()], "")},
                                       role );
                  });
  });

  self.findHandlerRole = function( roleId ) {
    return _.find( self.applicationHandlerRoles(),
                 {id: ko.unwrap( roleId )});
  };

  function updateHandler( handlerId, data) {
    // Peeks are needed so the changes (e.g., handler removal) will
    // not trigger unnecessary ajax call via computed in
    // processRawHandler (see below).
    var h = _.find( self.applicationHandlers.peek(), {id: handlerId});
    if( h ) {
      var auth = _.find( self.authorities.peek(), {id: data.userId });
      if( auth ) {
        h.firstName(_.get(auth, "firstName", "" ));
        h.lastName( _.get(auth, "lastName", "" ));
      }
      var roleName = _.get(_.find( self.applicationHandlerRoles.peek(), {id: data.roleId}),
                       "name");
      if( roleName ) {
        h.roleName( roleName );
      }
      
      if( data.userId && data.roleId) {
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
                                                     "firstName", "lastName",
                                                     "roleName"])),
                         raw);
    ko.computed( function() {
      var data = {userId: m.userId(),
                  roleId: m.roleId()};
      if( !ko.computedContext.isInitial()) {
        updateHandler( raw.id, data);
      }
    });
    return m;
  }

  
  self.newApplicationHandler = function() {
    self.applicationHandlers.push(processRawHandler( {id: _.uniqueId( TMP ),
                                                      roleId: ko.observable(),
                                                      roleName: ko.observable(),
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

  function fetchApplicationHandlers() {
    ajax.query( "application-handlers", {id: appId()})
    .success( function( res ) {
      self.applicationHandlers( _.map( res.handlers,
                                       processRawHandler ));
    })
    .call();
  }

  function fetchAuthorities() {
    if( allowed( "application-authorities")) {
      ajax.query( "application-authorities", {id: appId()})
      .success( function( res ) {
        self.authorities( res.authorities );
      })
      .call();
    }
  }

  hub.subscribe( "contextService::enter",
                 function() {
                   fetchApplicationHandlers();
                   fetchAuthorities();
                 });

  hub.subscribe( "contextService::leave", function() {
    self.applicationHandlers.removeAll();
    self.authorities.removeAll();
  });
};
