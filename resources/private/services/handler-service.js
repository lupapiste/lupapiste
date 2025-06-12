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

  var appId = lupapisteApp.services.contextService.applicationId;
  var authModel = lupapisteApp.models.applicationAuthModel;

  function indicate( message ) {
    hub.send( "indicator", {message: message,
                            style: "positive"} );
  }

  var indicateSaved     = _.wrap( "saved", indicate );
  var indicateRecovered = _.wrap( "statement.palautettu", indicate );
  var indicateRemoved   = _.wrap( "calendar.deleted", indicate );

  // ---------------------------
  // Ajax API
  // ---------------------------

  self.pending = ko.observable();

  function fetch( queryName, successFun ) {
    if( authModel.ok( queryName )) {
      ajax.query( queryName, {id: appId()} )
      .success( successFun )
      .call();
    }
  }

  function fetchAll() {
    fetch( "application-handlers",
           function( res ) {
             self.applicationHandlers( _.map( res.handlers,
                                              processRawHandler ));
           });
    fetch( "application-authorities",
           function( res ) {
             authorities( res.authorities );
           });
    fetch( "application-organization-handler-roles",
           function( res ) {
             roles( _.map( res.handlerRoles,
                           function( role ) {
                             return _.defaults( {
                               name: _.get( role, ["name",
                                                   loc.getCurrentLanguage()], "")
                             },
                                                role );
                           }));
           });
  }

  function resetAll() {
    self.applicationHandlers.removeAll();
    authorities.removeAll();
    roles.removeAll();
  }

  var latest = null;

  function current() {
    return {appId: appId(),
            state: lupapisteApp.models.application.state()};
  }

  ko.computed( function() {
    var canFetch = _.some(authModel.getData()) && appId();
    if( canFetch ) {
      if( appId() && !_.isEqual( current(), latest ) ) {
        ko.ignoreDependencies( fetchAll );
        latest = current();
      }
    } else {
      ko.ignoreDependencies( resetAll );
      latest = null;
    }
  });

  var notifyRoleChange = _.wrap( "handlerService::role-change", hub.send );

  // Name is object (e.g., {fi: "Uusi nimi", sv: "Nytt namn", en: "New name"}).
  function upsertOrganizationRole( roleId, name ) {
    ajax.command( "upsert-handler-role",
                  _.defaults({name: name},
                             isTemporary(roleId.peek()) ? {} : {roleId: roleId.peek()}))
    .success( function( res ) {
      roleId( res.id );
      indicateSaved();
      notifyRoleChange();
    } )
    .call();
  }

  function toggleOrganizationRole( roleId, enabled ) {
    ajax.command( "toggle-handler-role", {roleId: roleId, enabled: enabled})
      .success( function( res ) {
        (enabled ? indicateRecovered : indicateRemoved)( res );
        notifyRoleChange();
      })
      .call();
  }

  function applicationSuccessFn( indicatorFn, response ) {
    hub.send( "assignmentService::applicationAssignments",
              {applicationId: appId()});
    indicatorFn( response );
  }

  function upsertHandler( handlerId, data ) {
    ajax.command( "upsert-application-handler",
                  _.defaults( data,
                              isTemporary( handlerId.peek() )
                                   ? {}
                                   : {handlerId: handlerId.peek()},
                            {id: appId()}))
      .pending( self.pending )
      .success( function( res ) {
        handlerId( res.id );
        applicationSuccessFn( indicateSaved );
      } )
      .call();
  }

  function removeHandler( handlerId ) {
    ajax.command( "remove-application-handler",
                  {id: appId(),
                   handlerId: ko.unwrap( handlerId )})
      .pending( self.pending )
      .success( _.partial( applicationSuccessFn, indicateRemoved))
      .call();
  }

 // ---------------------------

  self.applicationAuthorities = authorities;

  self.applicationHandlerRoles = roles;

  function processRawRole( role ) {
    var nameObs = ko.mapping.fromJS( role.name );
    var roleObs = ko.observable( role.id );
    ko.computed( function() {
      if( _.every( _.values( nameObs), _.flow( ko.unwrap, _.trim))
       && !ko.computedContext.isInitial()) {
        upsertOrganizationRole( roleObs, ko.mapping.toJS( nameObs ));
      }
    });
    return _.defaults( {id: roleObs,
                        name: nameObs,
                        disabled: ko.observable( role.disabled)},
                       role);
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
               .map( processRawRole )
               .value());
      }
    });
    return roles;
  };

  self.organizationLanguages = ko.pureComputed( function() {
    return _.keys( ko.mapping.toJS( _.get( roles(),
                                           "0.name",
                                           {})));
  });

  self.toggleOrganizationHandlerRole = function( roleId, disabled ) {
    if( !isTemporary( roleId )) {
      toggleOrganizationRole( roleId(), !disabled() );
    }
  };

  self.isTemporaryRole = function(role) {
    return _.startsWith(ko.unwrap(_.get(ko.unwrap(role), "id")), TMP);
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
    var auth = _.find( authorities(), {id: data.userId });
    if( auth ) {
      handler.firstName(_.get(auth, "firstName", "" ));
      handler.lastName( _.get(auth, "lastName", "" ));
    }
    var roleName = _.get(_.find( roles(), {id: data.roleId}),
                         "name");
    if( roleName ) {
      handler.roleName( roleName );
    }
    if( data.userId && data.roleId ) {
      upsertHandler(handler.id, data);
    }
  }

  function processRawHandler( raw ) {
    var m =   ko.mapping.fromJS( raw );
    ko.computed( function() {
      var data = {userId: m.userId(),
                  roleId: m.roleId()};
      if( !ko.computedContext.isInitial()) {
        ko.ignoreDependencies( _.partial( updateHandler, m, data ));
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

};
