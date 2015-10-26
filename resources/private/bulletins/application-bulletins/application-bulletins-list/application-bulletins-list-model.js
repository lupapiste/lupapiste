LUPAPISTE.ApplicationBulletinsListModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  self.query = params.query;

  self.columns = [
    util.createSortableColumn("first", "bulletin.state", {sortField: "bulletinState", currentSort: self.query.sort}),
    util.createSortableColumn("second", "bulletin.municipality", {sortField: "municipality", currentSort: self.query.sort}),
    util.createSortableColumn("third", "bulletin.location", {sortField: "address", currentSort: self.query.sort}),
    util.createSortableColumn("fourth", "bulletin.type", {sortable: false}),
    util.createSortableColumn("fifth", "bulletin.applicant", {sortField: "applicant", currentSort: self.query.sort}),
    util.createSortableColumn("sixth", "bulletin.date", {sortField: "modified", currentSort: self.query.sort}),
    util.createSortableColumn("seventh", "bulletin.feedback-period", {sortable: false})
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
