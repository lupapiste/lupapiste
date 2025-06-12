LUPAPISTE.ApplicationBulletinsListModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  var currentSort = {field: ko.observable(params.sort.field()), asc: ko.observable(params.sort.asc())};

  self.columns = [
    util.createSortableColumn("zeroth", "bulletin.process", {sortable: false}),
    util.createSortableColumn("first", "bulletin.state", {sortField: "bulletinState", currentSort: currentSort}),
    util.createSortableColumn("second", "bulletin.municipality", {sortField: "municipality", currentSort: currentSort}),
    util.createSortableColumn("third", "bulletin.location", {sortField: "address", currentSort: currentSort}),
    util.createSortableColumn("fourth", "bulletin.type", {sortable: false}),
    util.createSortableColumn("fifth", "bulletin.date", {sortField: "modified", currentSort: currentSort}),
    util.createSortableColumn("sixth", "bulletin.feedback-period", {sortable: false})
  ];

  self.bulletins = ko.pureComputed(function () {
    return _.map(params.bulletins(), function (bulletin) {
      var commentingType, commentingEndsAt, enddate;
      if (bulletin.proclamationEndsAt) {
        commentingType = loc("bulletin.comment.period");
        enddate = moment(bulletin.proclamationEndsAt).endOf("day");
        commentingEndsAt = enddate.isAfter(moment()) ?
          enddate.format("D.M.YYYY") : loc("bulletin.period.ended");
      } else if (bulletin.appealPeriodEndsAt) {
        commentingType = loc("bulletin.appeal.period");
        enddate = moment(bulletin.appealPeriodEndsAt).endOf("day");
        commentingEndsAt = enddate.isAfter(moment()) ?
          enddate.format("D.M.YYYY") : loc("bulletin.period.ended");
      }

      var opDesc = _.get(bulletin, "bulletinOpDescription");

      return {
        id: bulletin.id,
        bulletinProcess: ["bulletin.process", bulletin.bulletinProcess],
        bulletinState: bulletin.bulletinState,
        bulletinStateLoc: ["bulletin", "state", bulletin.bulletinState],
        municipality: "municipality." + bulletin.municipality,
        address: bulletin.address,
        type: opDesc || loc("operations." + bulletin.primaryOperation.name),
        date: bulletin.proclamationStartsAt || bulletin.appealPeriodStartsAt,
        commentingType: commentingType,
        commentingEndsAt: commentingEndsAt,
        category: bulletin.category
      };
    });
  });

  self.gotResults = ko.pureComputed(function() {
    return self.bulletins().length !== 0;
  });

  ko.computed(function() {
    hub.send("bulletinService::sortChanged", {
      sort: ko.mapping.toJS(currentSort)
    });
  });

  self.openBulletin = function(item) {
    if (_.get(item, "category") === "ymp") {
      pageutil.openPage("ymp-bulletin", item.id);
    } else {
      pageutil.openPage("bulletin", item.id);
    }
  };

  self.onRowKeyup = function(item, event) {
    if (event.key === "Enter") {
      self.openBulletin(item);
    }
  };
};
