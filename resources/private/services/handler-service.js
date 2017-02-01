// Application handlers and organization handler roles.
LUPAPISTE.HandlerService = function() {
  "use strict";
  var self = this;

  var TMP = "temporary-";
  
  var authorities = ko.observableArray();
  self.applicationHandlers = ko.observableArray();
  var roles = ko.observableArray();
  
  // Ajax API

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
        authorities( res.authorities );
      })
      .call();
    }
  }

function fetchHandlerRoles() {
  if( allowed( "application-organization-handler-roles")) {
      ajax.query( "application-organization-handler-roles", {id: appId()})
      .success( function( res ) {
        roles( _.map( res.handlerRoles,
                  function( role ) {
                    return _.defaults( {name: _.get( role, ["name", loc.getCurrentLanguage()], "")},
                                       role );
                  }));
      })
      .call();
    }
  }

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
  
  self.organizationHandlerRoles = function( organization ) {
    ko.computed( function() {
      var orgId = util.getIn( organization, ["organizationId"]);
      var rawRoles = util.getIn( organization, ["handlerRoles"]);
      // There is always at least one role (general).
      if( orgId && orgId !== latestOrgId && _.size( rawRoles) ) {
        roles.removeAll();
        latestOrgId = orgId;
        roles(_.map( rawRoles,
                     _.partial( processRawRole, orgId)));
      }                        
    });    
    
    return roles;
  };

  self.organizationLanguages = function() {
    return _.intersection( loc.getSupportedLanguages(),
                           _.keys( _.get( roles(),
                                          "0.name")));
  };

  self.removeOrganizationHandlerRole = function( roleId ) {
    roles.remove( function( r ) {return r.id === roleId;});
  };

  self.addOrganizationHandlerRole = function() {
    roles.push( processRawRole(latestOrgId,
                               {id: _.uniqueId( TMP ),
                                name: _( self.organizationLanguages())
                                      .map( function( lang ) {return [lang, ""];})
                                      .fromPairs()
                                      .value()}));    
  };


  

  self.findHandlerRole = function( roleId ) {
    return _.find( roles(),
                 {id: ko.unwrap( roleId )});
  };

  function updateHandler( handlerId, data) {
    // Peeks are needed so the changes (e.g., handler removal) will
    // not trigger unnecessary ajax call via computed in
    // processRawHandler (see below).
    var h = _.find( self.applicationHandlers.peek(), {id: handlerId});
    if( h ) {
      var auth = _.find( authorities.peek(), {id: data.userId });
      if( auth ) {
        h.firstName(_.get(auth, "firstName", "" ));
        h.lastName( _.get(auth, "lastName", "" ));
      }
      var roleName = _.get(_.find( roles.peek(), {id: data.roleId}),
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


  hub.subscribe( "contextService::enter", fetchApplicationHandlers );

  hub.subscribe( "contextService::leave", function() {
    self.applicationHandlers.removeAll();
    authorities.removeAll();
    roles.removeAll();
  });
};
