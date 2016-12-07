LUPAPISTE.AssignmentsSearchResultsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchResultsModel(params));

  self.results = self.dataProvider.results;

  self.openApplication = function(model) {
    var target = null;
    switch (model.target.group) {
      case "documents":
        target = "info";
        break;
      case "parties":
        target = "parties";
        break;
      case "attachments":
        target = "attachments";
        break;
    }
    pageutil.openApplicationPage(model.application, target);
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
    util.createSortableColumn("seventh",   "common.created",
                              {sortable: true,
                               sortField: "created.timestamp", // added during assignment search aggregation
                               currentSort: self.dataProvider.sort}),
    util.createSortableColumn("eighth", null,    // Mark complete
                              {sortable: false})

  ];
};
