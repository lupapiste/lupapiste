// Parameters:
// organization: Organization model instance
LUPAPISTE.BulkChangeHandlersModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var authorities = ko.observableArray();
  var latestOrgId = null;

  self.oldHandler = ko.observable();
  self.newHandler = ko.observable();
  self.handlerRole = ko.observable();
  self.waiting = ko.observable();
  self.resultText = ko.observable();

  var organization = self.disposedComputed( function() {
    return params.organization;
  });

  var orgId = self.disposedComputed( function() {
    return util.getIn( organization(), ["id"] );
  });

  function fetchAuthorities() {
    authorities.removeAll();
    self.oldHandler( null );
    self.newHandler( null );
    self.handlerRole( null );

    latestOrgId = orgId();
    ajax.query( "organization-authorities", {organizationId: orgId(),
                                             includeDisabled: true})
      .success( function( res ) {
        authorities( res.authorities );
      })
      .call();
  }

  function userList( users ) {
    return _( users )
      .sortBy( ["lastName"])
      .map( function( a ) {
        return {text: sprintf( "%s %s (%s)", a.lastName, a.firstName, a.username),
                value: a.id};
      })
      .value();
  }

  self.allAuthorities = self.disposedComputed( function() {
    return userList( authorities() );
  });

  self.enabledAuthorities = self.disposedComputed( function() {
    return userList( _.filter( authorities(), "enabled" ));
  });


  self.states = _(["acknowledged", "agreementPrepared", "agreementSigned", "answered",
                   "appealed", "archived", "canceled", "closed", "complementNeeded",
                   "constructionStarted", "draft", "extinct", "final", "finished",
                   "foremanVerdictGiven", "hearing", "inUse", "info", "onHold", "open",
                   "proposal", "proposalApproved", "ready", "registered", "sent",
                   "sessionHeld", "sessionProposal", "submitted", "survey", "underReview",
                   "verdictGiven"])
    .map( function( state ) {
      return {state: state,
              value: ko.observable( false ),
              text: sprintf( "%s (%s)", loc( state), state)};
    })
    .sortBy( ["text"])
    .value();

  self.handlerRoles = self.disposedComputed( function() {
    return _( util.getIn( organization(), ["handler-roles"] ))
      .map( function( h ) {
        return {text: _.get( h, "name." + loc.getCurrentLanguage()),
                id: h.id};
      })
      .sortBy( ["text"])
      .value();
  });

  var selectedStates = self.disposedComputed( function() {
    return _( self.states )
      .filter( function( state ) {
        return state.value();
      })
      .map( function( state ) {
        return state.state;
      })
      .value();
  });

  self.canExecute = self.disposedComputed( function() {
    self.resultText( null );
    return self.oldHandler()
      && self.newHandler()
      && (self.oldHandler() !== self.newHandler())
      && self.handlerRole()
      && _.size( selectedStates() );
  });

  self.changeHandler = function() {
    self.resultText( null );
    ajax.command( "change-handler-applications",
                  {organizationId: orgId(),
                   roleId: self.handlerRole(),
                   oldUserId: self.oldHandler(),
                   newUserId: self.newHandler(),
                   states: selectedStates()})
      .pending( self.waiting )
      .processing( self.waiting )
      .success( function( res ) {
        switch( res.count ) {
        case 0:
          self.resultText( loc( "bulk-change-handlers.no-results"));
          break;
        case 1:
          self.resultText( loc( "bulk-change-handlers.one-result"));
          break;
        default:
          self.resultText( loc( "bulk-change-handlers.results", res.count));
        }
      })
      .call();
  };

  self.disposedSubscribe( orgId, function( newOrgId) {
    if( newOrgId !== latestOrgId ) {
      fetchAuthorities();
      _.forEach( self.states, function( state ) {
        state.value( false );
      });
    }
  });

  fetchAuthorities();
};
