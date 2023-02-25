LUPAPISTE.AssignmentsDataProvider = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var defaultData = {
    searchResults: [],
    totalCount: -1,
    userTotalCount: -1
  };

  self.sort = params.sort ||
    {field: ko.observable("created"), asc: ko.observable(true)};
  self.data             = ko.observable(params.data || defaultData);
  self.results          = ko.observable(self.data().searchResults);
  self.limit            = params.currentLimit;
  self.skip             = ko.observable(0);
  self.searchResultType = ko.observable(params.searchResultType);

  self.state            = ko.pureComputed(function () {
      return self.searchResultType() === "automatic" ? "created" : self.searchResultType();
  });

  self.trigger          = ko.pureComputed(function() {
      switch (self.searchResultType()) {
          case "automatic": return "not-user-created";
          case "all":       return "any";
          case "completed": return "any";
          default:          return "user-created";
      }
  });

  self.assignmentsCount = ko.observable(0); // Count of open assignments received by the current user.

  self.pending = ko.observable(false);

  self.hasResults = self.disposedPureComputed(function() {
    return !_.isEmpty(self.data().searchResults);
  });

  self.totalCount = self.disposedPureComputed(function() {
    return self.data().totalCount;
  });

  var stateClasses = {
    created: "lupicon-circle-attention",
    "targets-added": "lupicon-circle-attention",
    completed: "lupicon-circle-check"
  };

  function enrichAssignmentData(assignment) {
    var createdState = _.findLast(assignment.states,
                                  function(state) {
                                    return _.includes( ["created", "targets-added"], state.type );
                                  });
    var currentState = _.maxBy(assignment.states, "timestamp");
    var completed = (currentState.type === "completed");
    return _.merge(assignment, {
      currentState: currentState,
      createdState: createdState,
      incomplete: !completed,
      creatorName: createdState.user.firstName + " " + createdState.user.lastName,
      statusClass: stateClasses[currentState.type],
      addressAndMunicipality: assignment.application.address + ", " + loc(["municipality", assignment.application.municipality]),
      targetGroup: loc("application.assignment.type." + assignment.targets[0].group),
      targetType: loc(assignment.targets[0]["type-key"]),
      targetInfo: assignment.targets[0]["info-key"] && loc(assignment.target[0]["info-key"]) || assignment.targets[0].description
    });
  }

  self.onSuccess = function(res) {
    var assignments = _.map(res.data.assignments, enrichAssignmentData);
    self.data(_.defaults({searchResults: assignments,
                          totalCount: res.data.totalCount},
                          defaultData));
    self.results(assignments);
  };

  self.searchField = ko.observable("");
  self.searchFieldDelayed = ko.pureComputed(self.searchField)
    .extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 750}});

  self.searchStartDate = ko.observable();
  self.searchEndDate = ko.observable();

  var dashboardFields = ko.observable();

  var searchFields = ko.pureComputed(function() {
    var fields = dashboardFields();
    return fields
      ? _.merge( {
        limit: self.limit(),
        skip: self.skip(),
        trigger: self.trigger(),
        state: self.state()
      },
                 fields,
                 {sort: ko.mapping.toJS( self.sort ),
})
      : null;
  });

  function loadAssignments() {
    if (pageutil.getPage() === "applications") {
      if( searchFields() ) {
        ajax.datatables("assignments-search", searchFields())
          .success(self.onSuccess)
          .onError("error.unauthorized", notify.ajaxError)
          .pending(self.pending)
          .call();
      }

      ajax.query("assignment-count")
        .success(function(response) {
          self.assignmentsCount(response.assignmentCount);
          hub.send( "assignmentsDataProvider::assignmentCount",
                    {count: response.assignmentCount});
        })
        .error(_.noop)
        .call();
    }
  }

  hub.onPageLoad("applications", loadAssignments);

  hub.subscribe("assignmentService::assignmentCompleted", loadAssignments);

  ko.computed(loadAssignments).extend({deferred: true});

  self.addHubListener( "Dashboard::search-assignments",
                       function( event ) {
                         // New search, reset skip
                         self.skip( 0 );
                         dashboardFields( event.fields );
                       });

};
