LUPAPISTE.ApplicationBulletinsListModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  var currentSort = params.sort;

  self.openBulletin = function(item) {
    pageutil.openPage("bulletin", item.id);
  };

  self.columns = [
    util.createSortableColumn("first", "bulletin.state", {sortField: "bulletinState", currentSort: currentSort}),
    util.createSortableColumn("second", "bulletin.municipality", {sortField: "municipality", currentSort: currentSort}),
    util.createSortableColumn("third", "bulletin.location", {sortField: "address", currentSort: currentSort}),
    util.createSortableColumn("fourth", "bulletin.type", {sortable: false}),
    util.createSortableColumn("fifth", "bulletin.applicant", {sortField: "applicant", currentSort: currentSort}),
    util.createSortableColumn("sixth", "bulletin.date", {sortField: "modified", currentSort: currentSort}),
    util.createSortableColumn("seventh", "bulletin.feedback-period", {sortable: false})
  ];

  ko.computed(function() {
    hub.send("bulletinService::fetchBulletins", {
      page: 1,
      sort: ko.mapping.toJS(currentSort)
    });
  });

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
