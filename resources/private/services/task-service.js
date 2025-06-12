// Task (foremen, reviews, plans) related service functionality
LUPAPISTE.TaskService = function() {
  "use strict";
  var self = this;

  var latestTaskCount = 0;
  var latestAppId = null;

  // Array of foreman roles (kuntaroolikoodi)
  self.foremanOrder = ko.observableArray();
  // Review task ids
  self.reviewOrder = ko.observableArray();
  // Plan and condition task ids
  self.planOrder = ko.observableArray();

  self.buildings = ko.observableArray();

  self.requestForm = ko.observable();

  // Auth model like observable for reviews category
  var reviewAuthModel = ko.observable();

  function hasAuth( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  }

  function reset() {
    latestTaskCount = 0;
    latestAppId = null;
    self.foremanOrder.removeAll();
    self.reviewOrder.removeAll();
    self.planOrder.removeAll();
    reviewAuthModel({});
    self.buildings([]);
    self.requestForm( null );
  }

  function fetchTaskOrder( event ) {
    var taskCount = _.size( util.getIn ( lupapisteApp, ["models", "application", "tasks"]) );
    if ( hasAuth( "task-order")
         && ( taskCount !== latestTaskCount  || event.applicationId !== latestAppId )) {
      latestTaskCount = taskCount;
      latestAppId = event.applicationId;
      ajax.query( "task-order", {id: event.applicationId })
        .success( function( res ) {
          self.foremanOrder( res.foremen || [] );
          self.reviewOrder( res.reviews || [] );
          self.planOrder( res.plans || [] );
        })
        .call();
    }
  }

  function refreshReviewAuths( event ) {
    var appId = _.get( event, "applicationId", latestAppId );
    ajax.query( "allowed-actions-for-category", {id: appId, category: "reviews"})
      .success( function( response ) {
        reviewAuthModel( response.actionsById );
      })
      .error( _.wrap( {}, reviewAuthModel ))
      .call();
  }

  self.reviewAuthOk = function( taskId, action ) {
    return util.getIn( reviewAuthModel, [taskId, action, "ok"] );
  };

  self.requestReview = function( taskId, requestData, waitingObs ) {
    ajax.command( "request-review", {id: latestAppId,
                                     taskId: taskId,
                                     request: requestData })
      .processing( waitingObs )
      .success( function() {
        lupapisteApp.models.application.lightReload();
        self.requestForm( null );
      })
      .call();
  };

  self.cancelReviewRequest = function( taskId ) {
    ajax.command( "cancel-review-request", {id: latestAppId,
                                            taskId: taskId})
      .success( lupapisteApp.models.application.lightReload )
      .call();
  };

  self.operationBuildings = function( operationIds ) {
    return _.filter( self.buildings(),
                     function( build ) {
                       return _.includes( operationIds, build.opId );
                     });
  };

  self.fetchBuildings = function( callback, event ) {
    if( hasAuth( "application-operation-buildings") ) {
      ajax.query( "application-operation-buildings",
                  {id: _.get( event, "applicationId", latestAppId )})
        .success( function( res) {
          callback( _.map( res.buildings,
                           function( m ) {
                             return _.set( m, "text",
                                           util.nonBlankJoin( [loc( "operations." + m.opName),
                                                               m.description,
                                                               m.nationalId],
                                                              " - "));
                           }));
        } )
        .call();
    } else {
      callback( {} );
    }
  };

  function fetchAll( event ) {
    fetchTaskOrder( event );
    refreshReviewAuths( event );
    self.fetchBuildings( self.buildings, event );
  }

  hub.subscribe( "contextService::enter", fetchAll );
  hub.subscribe( "contextService::leave", reset );
  hub.subscribe( "application-model-updated", fetchAll);
};
