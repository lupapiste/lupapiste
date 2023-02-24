/*
 * Assignments for application
 *
 */
LUPAPISTE.AssignmentService = function(applicationAuthModel) {
  "use strict";
  var self = this;

  var _data = ko.observableArray([]);

  self.targetTab = function (assignment) {
    var group = assignment.targets[0].group;
    if( _.startsWith(  group, "notice-forms-" )
        || _.includes( ["foremen", "review-request"], group )) {
      return "tasks";
    }
    if( _.includes( ["parties", "attachments"], group )) {
      return group;
    }
    return "";
  };

  function enrichAssignment(assignment) {
    return _.merge(assignment,
                   {createdState: _.find(assignment.states, function(state) { return state.type === "created"; }),
                    currentState: _.maxBy(assignment.states, "timestamp"),
                    targetTab: self.targetTab(assignment),
                    edit: ko.observable(false)});
  }


  function filterIncompleteAssignments(assignments) {
    return _.filter(assignments, function(a) { return _.get(a, "currentState.type") !== "completed"; });
  }

  // both user generated and automatic assignments
  var allAssignments = ko.pureComputed(function() {
    return _.map(_data(), enrichAssignment);
  });

  self.assignments = ko.pureComputed(function() {
    return _.filter(allAssignments(), function(a) {
      return a.trigger === "user-created";
    });
  });

  self.incompleteAssignments = ko.pureComputed(function() {
    return filterIncompleteAssignments(self.assignments());
  });

  // Due to historical reasons, automatic refers to attachments here.
  self.automaticAssignments = ko.pureComputed(function() {
    return _.filter(allAssignments(), function(a) {
      return !_.includes( ["user-created", "notice-form", "foreman", "review"],
                          a.trigger )
        && _.get(a, "currentState.type") !== "completed";
    });
  });

  self.noticeFormAssignments = ko.pureComputed( function() {
    return filterIncompleteAssignments( _.filter( allAssignments(), {trigger: "notice-form"}));
  });

  self.foremanAssignments = ko.pureComputed( function() {
    return filterIncompleteAssignments( _.filter( allAssignments(), {trigger: "foreman"}));
  });

  self.reviewAssignments = ko.pureComputed( function() {
    return filterIncompleteAssignments( _.filter( allAssignments(), {trigger: "review"}));
  });


  /*
   * Targets are two levels deep and represented as objects.
   * Keys are the "target groups", and value for each key is array of corresponding items in application.
   * Example with only parties group:
   * {"parties": [{id: "5808c517f16562feee6856fb", type: "paasuunnittelija", description: "Antero Arkkitehti"},
                  {id: "5808c517f16562feee6856fc", type: "suunnittelija", info-key: "info.text.key"}]}
   */
  self.targets = ko.observableArray([]);

  function assignmentTargetsQuery(id) {
    if (applicationAuthModel.ok("assignment-targets")) {
      ajax.query("assignment-targets", {id: id, lang: loc.getCurrentLanguage()})
        .success(function(resp) {
          self.targets(_.fromPairs(resp.targets));
        })
        .error( function() {
          self.targets([]);
        })
        .call();
    }
  }

  function assignmentsForApplication(id) {
    if (id && applicationAuthModel.ok("assignments-for-application")) {
      ajax.query("assignments-for-application", {id: id})
        .success(function(resp) {
          _data(resp.assignments);
          _.delay(window.Stickyfill.rebuild,0);
        })
        .call();
    }
  }

  hub.subscribe("assignmentService::saveAssignment", function(event) {
    var assignment = _.omit(event, "eventType");
    var commandName = util.isEmpty(assignment.assignmentId) ? "create-assignment" : "update-assignment";

    ajax.command(commandName, assignment)
      .success(function(resp) {
        util.showSavedIndicator(resp);
        assignmentsForApplication(event.id);
      })
      .call();
  });

  hub.subscribe("assignmentService::markComplete", function(event) {
    ajax.command("complete-assignment", {assignmentId: _.get(event, "assignmentId")})
      .success(function(resp) {
        util.showSavedIndicator(resp);
        hub.send("assignmentService::assignmentCompleted", null);
        var appId = util.getIn(event, ["applicationId"]);
        if (appId) { // refresh application assignments
          assignmentsForApplication(appId);
        }
      })
      .onError("error.assignment-not-completed", util.showSavedIndicator)
      .call();
  });

  hub.subscribe("assignmentService::targetsQuery", function(event) {
    assignmentTargetsQuery(_.get(event, "applicationId"));
  });

  hub.subscribe("assignmentService::applicationAssignments", function(event) {
    assignmentsForApplication(_.get(event, "applicationId"));
  });

  hub.subscribe("application-model-updated", function(event) {
    if (!_.isEmpty(event.applicationId)) {
      assignmentsForApplication(_.get(event, "applicationId"));
    }
  });

  function assignmentTargetIds(assignments) {
    return _.map(_.flatten(_.map(assignments, "targets")), "id");
  }

  function attachmentIsTargetedByAutomaticAssignments(event) {
    return _.includes(assignmentTargetIds(self.automaticAssignments()), _.get(event, "attachmentId"));
  }

  // When attachment is removed, reload assignments if the assignment was a target for an automatic assignment
  hub.subscribe({
    eventType: "attachmentsService::remove",
    ok:        true
  }, function(event) {
    if (attachmentIsTargetedByAutomaticAssignments(event)) {
      assignmentsForApplication(_.get(event, "id"));
    }
  });
  hub.subscribe({
    eventType:   "attachmentsService::update",
    ok:          true,
    commandName: "set-attachment-type"
  }, function(event) {
    assignmentsForApplication(_.get(event, "id"));
  });
  hub.subscribe({
    eventType: "attachment-upload::finished",
    ok:        true
  }, function(event) {
    assignmentsForApplication(ko.unwrap(_.get(event, "id")));
  });

  var debounceAssignmentsForApplication = _.debounce(function(id) {
      assignmentsForApplication(id);
    },
    500);

  hub.subscribe({
    eventType: "attachmentsService::bind-attachments-status",
    status:    "done"
  }, function(event) {
    debounceAssignmentsForApplication(ko.unwrap(_.get(event, "applicationId")));
  });

  var unwrapped = ko.pureComputed(_.wrap(_data, ko.mapping.toJS));

  unwrapped.subscribe(function() {
    hub.send("assignmentService::changed-cljs");
  });
};
