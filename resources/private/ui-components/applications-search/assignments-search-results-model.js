LUPAPISTE.AssignmentsSearchResultsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchResultsModel(params));

  self.results = self.dataProvider.results;
  self.completeEnabled = lupapisteApp.models.globalAuthModel.ok("complete-assignment");

  self.openApplication = function(model) {
    pageutil.openApplicationPage(model.application,
                                 lupapisteApp.services.assignmentService.targetTab( model ));
  };

  self.keyOpenApplication = function(model, event ) {
    if (event.keyCode === 13) {
      self.openApplication( model );
    }
  };

  self.markComplete = function(id) {
    return function() {
      hub.send("assignmentService::markComplete", {assignmentId: id});
    };
  };

  self.columns = [
    util.createSortableColumn("first",   "application.assignment.status",
                              {sortable: false}),
    util.createSortableColumn("second",  "applications.id",
                              {sortable: true,
                               sortField: "application.id",
                               currentSort: self.dataProvider.sort}),
    util.createSortableColumn("third",  "applications.location",
                              {sortable: true,
                               sortField: "application.address",
                               currentSort: self.dataProvider.sort}),
    util.createSortableColumn("fourth",  "application.assignment.subject",
                              {sortable: false}),
    util.createSortableColumn("fifth",   "common.description",
                              {sortable: true,
                               sortField: "description-ci",
                               currentSort: self.dataProvider.sort}),
    util.createSortableColumn("sixth", "application.assignment.creator",
                              {sortable: false}),
    util.createSortableColumn("seventh",   "application.assignment.modified",
                              {sortable: true,
                               sortField: "modified",
                               currentSort: self.dataProvider.sort}),
    util.createSortableColumn("eighth", null,    // Mark complete
                              {sortable: false})

  ];
};
