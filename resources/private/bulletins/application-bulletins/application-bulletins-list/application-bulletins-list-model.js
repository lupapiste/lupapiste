LUPAPISTE.ApplicationBulletinsListModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  var sort = {field: ko.observable(""), asc: ko.observable(false)};

  self.columns = [
    util.createSortableColumn("first", "bulletin.state", {sortField: "bulletinState", currentSort: sort}),
    util.createSortableColumn("second", "bulletin.municipality", {sortField: "municipality", currentSort: sort}),
    util.createSortableColumn("third", "bulletin.location", {sortField: "location", currentSort: sort}),
    util.createSortableColumn("fourth", "bulletin.type", {sortField: "type", currentSort: sort}),
    util.createSortableColumn("fifth", "bulletin.applicant", {sortField: "applicant", currentSort: sort}),
    util.createSortableColumn("sixth", "bulletin.date", {sortField: "modified", currentSort: sort}),
    util.createSortableColumn("seventh", "bulletin.feedback-period", {sortField: "feedbackPeriod", currentSort: sort})
  ];

  self.bulletins = ko.pureComputed(function () {
    return _.map(params.bulletins(), function (bulletin) {
      return {
        id: bulletin.id,
        bulletinState: bulletin.bulletinState,
        bulletinStateLoc: ["bulletin", "state", bulletin.bulletinState],
        municipality: "municipality." + bulletin.municipality,
        address: bulletin.address,
        type: "operations." + bulletin.primaryOperation.name,
        applicant: bulletin.applicant,
        date: bulletin.modified,
        feedbackPeriod: "TODO"
      };
    });
  });
};
