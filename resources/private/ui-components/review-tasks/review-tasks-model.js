// Model for showing the review tasks table.
// Parameters:
// openTask: function to be called when the task name is
//           clicked. Receives task id as an argument.
LUPAPISTE.ReviewTasksModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var taskService = lupapisteApp.services.taskService;
  var assignmentService = lupapisteApp.services.assignmentService;

  self.showFaulty = ko.observable( false );

  self.requestForm = taskService.requestForm;

  self.openTask = function( data ) {
    if( _.isFunction( params.openTask )) {
      params.openTask( data.id );
    }
  };

  function reviewProperty( task, propName ) {
    return _.get( task, "data.katselmus." + propName + ".value");
  }

  function reviewObjectProperty( task, propName, subPropName ) {
    var value = reviewProperty( task, propName );
    return _.isObject(value) ? _.get(value, subPropName) : value;
  }

  function reviewIcons ( task ) {
    var icon = null;
    var ltext = null;
    switch( task.state) {
    case "faulty_review_task":
      icon = "";
      ltext = "task.faulty-review-task";
      break;
    case "sent":
      icon = "lupicon-circle-check";
      ltext = "review.state.ok";
      break;
    default:
      icon = "lupicon-circle-attention";
      ltext = "review.state.not-ok";
    }
    var hasAttachments = _.find( lupapisteApp.services.attachmentsService.attachments(),
                                 function( aObs ) {
                                   return _.get( aObs(), "target.id") === task.id;
                                 });
    return {state: icon,
            ltext: ltext,
            attachment: hasAttachments && "lupicon-paperclip"};
  }

  function requested( task ) {
    if( task.request && _.includes( ["requires_user_action", "requires_authority_action"],
                                   task.state )) {
      return loc( "review-request.requested", util.finnishDate( task.request.created ));
    }
  }

  function closeAssignment( assignmentId ) {
    hub.send( "assignmentService::markComplete",
              {assignmentId: assignmentId,
               // applicationId forces assignments refresh
               applicationId: lupapisteApp.models.application.id()});
  }

  function reviewAssignments( task ) {
    var enabled = lupapisteApp.models.applicationAuthModel.ok( "complete-assignment" );
    var assignments = _(assignmentService.reviewAssignments())
        .filter(function( a ) {
          return _.some( a.targets, {id: task.id});
        })
        .map( function( a ) {
          var name = util.nonBlankJoin([_.get( a, "recipient.firstName"),
                                        _.get( a, "recipient.lastName")],
                                       " ");
          return {name: _.isBlank( name )  ? "" : name + ": ",
                  text: requested( task ),
                  id: a.id,
                  closeFn: _.wrap( a.id, closeAssignment ),
                  enabled: enabled
                 };
        })
        .value();
    return _.isEmpty( assignments ) ? null : assignments;
  }

  function requestDetails( task ) {
    if( requested( task ) ) {
      var details = task.request.details;
      var buildings = taskService.operationBuildings( details["operation-ids"] );
      var m = _.pick( details, ["message", "contact"]);
      return _.isEmpty( buildings ) ? m : _.set( m, "buildings", buildings );
    }
  }

  function canRequest( task ) {
    // Only one request form can be open at the time. This
    // limititation ensures that the tasks can be safely refreshed
    // when the form is closed.
    return !self.requestForm()
      && taskService.reviewAuthOk( task.id, "request-review" );
  }

  function applicationTasks() {
    var app = lupapisteApp.models.application;
    return ko.mapping.toJS( app.tasks || []);
  }

  function isFaulty( task ) {
    return util.getIn( task, ["state"]) === "faulty_review_task";
  }

  self.hasFaulty = self.disposedPureComputed( function() {
    return _.some( applicationTasks(), isFaulty );
  });

  self.reviews = self.disposedComputed( function() {
    var tableTasks = applicationTasks();
    var order = taskService.reviewOrder();

    if( !self.showFaulty() ) {
      _.remove( tableTasks, isFaulty );
    }

    return _( _.map( order, function( id ) {
      return _.find( tableTasks, {id: id} );
    }))
      .filter()
      .map(function( task, i ) {
        var notDraft = (task.state === "requires_user_action" ? null : true);
        return {
          isOddRow: i % 2,
          notesVisible: ko.observable( false ),
          name: task.taskname,
          date: notDraft && reviewProperty( task, "pitoPvm"),
          author: notDraft && reviewObjectProperty( task, "pitaja", "name"),
          state: notDraft && reviewProperty( task, "tila"),
          taskState: notDraft && task.state,
          condition: util.getIn(task, ["data", "vaadittuLupaehtona", "value"]),
          notes: notDraft && reviewProperty( task, "huomautukset.kuvaus"),
          id: task.id,
          source: task.source,
          icons: reviewIcons( task ),
          canRequest: canRequest( task ),
          requested:  requested( task ),
          requestDetails: requestDetails( task ),
          showRequestDetails: ko.observable(),
          showRequestForm: self.disposedPureComputed( function() {
            return task.id === self.requestForm();
          }),
          assignments: reviewAssignments( task ),
        };
      })
      .value();
  });

  self.requestReview = function( task ) {
    hub.send( "show-dialog", {ltitle: "areyousure",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "review-request.confirmation",
                                               yesFn: _.wrap( task.id, taskService.requestReview),
                                               lyesTitle: "review-request.request",
                                               lnoTitle: "cancel"}});
  };
};
