// Application handlers and organization handler roles.  The service
// is used both by authorityAdmin (organisation handler roles) and
// applicant/authority (listing and editing application handlers).
LUPAPISTE.HandlerService = function() {
  "use strict";
  var self = this;

  var TMP = "temporary-";
  
  var authorities = ko.observableArray();
  self.applicationHandlers = ko.observableArray();
  var roles = ko.observableArray();

  function isTemporary( id ) {
    return _.startsWith( ko.unwrap(id), TMP );
  }

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

  function indicate( message ) {
    hub.send( "indicator", {message: message,
                            style: "positive"} );
  }

  var indicateSaved   = _.wrap( "saved", indicate );
  var indicateRemoved = _.wrap( "calendar.deleted", indicate );
 
  // ---------------------------
  // Ajax API
  // ---------------------------

  function fetchApplicationHandlers() {
    ajax.query( "application-handlers", {id: appId()})
    .success( function( res ) {
      self.applicationHandlers( _.map( res.handlers,
                                       processRawHandler ));
    })
    .call();
  }

  function fetchAuthorities() {
    ajax.query( "application-authorities", {id: appId()})
    .success( function( res ) {
      authorities( res.authorities );
    })
    .call();    
  }

  function fetchHandlerRoles() {
    ajax.query( "application-organization-handler-roles", {id: appId()})
    .success( function( res ) {
      roles( _.map( res.handlerRoles,
                    function( role ) {
                      return _.defaults( {
                        name: _.get( role, ["name",
                                            loc.getCurrentLanguage()], "")
                      },
                                         role );
                    }));
    })
    .call();  
  }
  
  // Name is object (e.g., {fi: "Uusi nimi", sv: "Nytt namn", en: "New name"}).
  function upsertOrganizationRole( roleId, name ) {
    ajax.command( "upsert-handler-role",
                  _.defaults({name: name},
                             isTemporary( roleId.peek() )
                                          ? {}
                                          : {roleId: roleId.peek()}))
    .success( function( res ) {
      roleId( res.id );
      indicateSaved();
    } )
    .call();
  }

  function disableOrganizationRole( roleId ) {
    ajax.command( "disable-handler-role", {roleId: roleId })
    .success( indicateRemoved )
    .call();
  }

  function upsertHandler( handlerId, data ) {
    ajax.command( "upsert-application-handler",
                  _.defaults( data,
                              isTemporary( handlerId.peek() )
                                   ? {}
                                   : {handlerId: handlerId.peek()},
                            {id: appId()}))
    .success( function( res ) {
      handlerId( res.id );
      indicateSaved();      
    } )
    .call();
  }

  function removeHandler( handlerId ) {
    ajax.command( "remove-application-handler",
                  {id: appId(),
                   handlerId: ko.unwrap( handlerId )})
    .success( indicateRemoved)
    .call();
  }

 // ---------------------------

  self.applicationAuthorities = function() {
    if( _.isEmpty( authorities() )) {
      fetchAuthorities();
    }
    return authorities;
  };

  self.applicationHandlerRoles = function() {
    if( _.isEmpty( roles() )) {
      fetchHandlerRoles();
    }
    return roles;
  };  

  function processRawRole( role ) {
    var nameObs = ko.mapping.fromJS( role.name );
    var roleObs = ko.observable( role.id );
    ko.computed( function() {
      if( _.every( _.values( nameObs), _.flow( ko.unwrap, _.trim))
       && !ko.computedContext.isInitial()) {
        upsertOrganizationRole( roleObs, ko.mapping.toJS( nameObs ));
      }
    });
    return _.defaults( {id: roleObs, name: nameObs}, role);
  }

  var latestOrgId = null;
  
  self.organizationHandlerRoles = function( organization ) {
    ko.computed( function() {
      var orgId = util.getIn( organization, ["organizationId"]);
      var rawRoles = util.getIn( organization, ["handlerRoles"]);
      // There is always at least one role (general).
      if( orgId && orgId !== latestOrgId && _.size( rawRoles) ) {
        roles.removeAll();
        latestOrgId = orgId;
        roles( _(rawRoles)
               .reject( {disabled: true})
               .map( processRawRole )
               .value());
      }                        
    });        
    return roles;
  };
  
  self.organizationLanguages = ko.pureComputed( function() {
    return _.intersection( loc.getSupportedLanguages(),
                           _.keys( _.get( roles(),
                                          "0.name")));
  });

  self.removeOrganizationHandlerRole = function( roleId ) {
    roles.remove( function( r ) {return r.id() === roleId();});
    if( !isTemporary( roleId )) {
      disableOrganizationRole( roleId() );
    }
  };

  self.addOrganizationHandlerRole = function() {
    roles.push( processRawRole({id: _.uniqueId( TMP ),
                                name: _( self.organizationLanguages())
                                      .map( function( lang ) {return [lang, ""];})
                                      .fromPairs()
                                      .value()}));    
  };

  self.findHandlerRole = function( roleId ) {
    return _.find( roles(),
                 {id: ko.unwrap( roleId )});
  };

  function updateHandler( handler, data) {
    // Peeks are needed so the changes (e.g., handler removal) will
    // not trigger unnecessary ajax call via computed in
    // processRawHandler (see below).
    var auth = _.find( authorities.peek(), {id: data.userId });
    if( auth ) {
      handler.firstName(_.get(auth, "firstName", "" ));
      handler.lastName( _.get(auth, "lastName", "" ));
    }
    var roleName = _.get(_.find( roles.peek(), {id: data.roleId}),
                         "name");
    if( roleName ) {
      handler.roleName( roleName );
    }
    
    if( data.userId && data.roleId) {
        upsertHandler(handler.id, data);        
      }
  }

  function processRawHandler( raw ) {
    var m =   ko.mapping.fromJS( raw );
    ko.computed( function() {
      var data = {userId: m.userId(),
                  roleId: m.roleId()};
      if( !ko.computedContext.isInitial()) {
        updateHandler( m, data);
      }
    });
    return m;
  }
  
  self.newApplicationHandler = function() {
    self.applicationHandlers.push(processRawHandler( {id: _.uniqueId( TMP ),
                                                      roleId: "",
                                                      roleName:"",
                                                      userId: "",
                                                      lastName: "",
                                                      firstName: ""}));
  };

  self.removeApplicationHandler = function( handlerId ) {
    self.applicationHandlers.remove( function( h ) {
      return h.id === handlerId;
    });
    if( !isTemporary( handlerId )) {
      removeHandler( handlerId );
    }
  };

  hub.subscribe( "contextService::enter", fetchApplicationHandlers );

  hub.subscribe( "contextService::leave", function() {
    self.applicationHandlers.removeAll();
    authorities.removeAll();
    roles.removeAll();
  });
};
