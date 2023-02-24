LUPAPISTE.EditHandlersModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.handlerService;

  function nameString( obj ) {
    return sprintf( "%s %s",
                    obj.lastName || "",
                    obj.firstName || "" );
  }

  var roles = service.applicationHandlerRoles;
  var authorities = ko.observableArray();

  function updateAuthorities( auths ) {
    authorities(_(auths)
                .filter( function( a ) {
                  return _.includes( a.roles, "authority" );
                })
                .map( function( auth ) {
                  return _.merge( {},
                                  auth,
                                  {id: auth.id,
                                   name: nameString( auth )});
                })
                .value());
  }

  self.disposedSubscribe(service.applicationAuthorities, updateAuthorities);

  // Initial call just in case. Otherwise coming back to component
  // would not not update authorities
  updateAuthorities( service.applicationAuthorities() );

  self.handlers = service.applicationHandlers;

  function currentUserIsHandler() {
    return _.some( self.handlers(), function( h ) {
      return h.userId() === lupapisteApp.models.currentUser.id();
    });
  }

  var currentUserWasHandler = currentUserIsHandler();

  function handlerRoles( handler ) {
    var usedIds = _(self.handlers())
                  .reject( {id: handler.id })
                  .map( function( h ) {
                    return h.roleId();
                  })
                  .value();
    return _.reject( roles(), function( role ) {
      return _.includes( usedIds, role.id )
          || role.disabled;
    });
  }

  function roleValue( handler ) {
    return self.disposedComputed( {
      read: function() {
        return {roleId: handler.roleId(),
                name: _.get( service.findHandlerRole( handler.roleId),
                             "name",
                             "" )};
      },
      write: function( role ) {
        handler.roleId( role.id );
      }
    });
  }

  function personValue( handler ) {
    var text = ko.observable( nameString( handler ));
    return self.disposedComputed( {
      read: function() {
        return {userId: handler.userId(),
                name: text()};
      },
      write: function( authority ) {
        handler.userId( authority.id );
        text( nameString( authority ));
      }
    });
  }

  var complete = self.disposedComputed( function() {
    return _.reduce( self.handlers(),
                   function( acc, h ) {
                     var queryRole = ko.observable( "" );
                     var selectedRole = roleValue( h );
                     var queryPerson = ko.observable( "" );
                     var selectedPerson = personValue( h );
                     return _.set( acc,
                                   h.id(),
                                   {roles: self.disposedComputed( function() {
                                     return util.filterDataByQuery( {data: handlerRoles( h ),
                                                                     query: queryRole(),
                                                                     selected: selectedRole(),
                                                                     label: "name"});
                                   }),
                                    queryRole: queryRole,
                                    selectedRole: selectedRole,
                                    persons: self.disposedComputed( function() {
                                      return util.filterDataByQuery( {data: authorities(),
                                                                      query: queryPerson(),
                                                                      selected: selectedPerson(),
                                                                      label: "name"});
                                   }),
                                   queryPerson: queryPerson,
                                   selectedPerson: selectedPerson}
                                 );
                   }, {});
  });

  self.getComplete = function( id ) {
    return _.get( complete(), [id()],{});
  };


  self.canAdd = function() {
    return _.some( handlerRoles( {}));
  };

  self.add = function() {
    service.newApplicationHandler();
  };

  self.remove = function( data ) {
    service.removeApplicationHandler( data.id );
  };

  function isDataDisabled( data ) {
    if( data && data.userId() && data.roleId() ) {
      var role = _.find( roles(), {id: data.roleId()});
      return !_.find( authorities(), {id: data.userId()})
          || !role
          || _.get( role, "disabled" );
    }
}

  self.isDisabled = function( data ) {
    return service.pending() || isDataDisabled( data );
  };

  self.back = function() {
    if( currentUserWasHandler !== currentUserIsHandler()) {
      repository.load( lupapisteApp.models.application.id());
    }
    hub.send(  "cardService::select", {deck: "summary",
                                       card: "info"});
  };
};
