LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;


  self.dataProvider = params.dataProvider;
  self.data = ko.pureComputed(function() {
    return _.map(self.dataProvider.applications(), function(item) {
      item.kuntalupatunnus = util.getIn(item, ["verdicts", 0, "kuntalupatunnus"]);
      if (item.foremanRole) {
        item.foremanRoleI18nkey = "osapuoli.tyonjohtaja.kuntaRoolikoodi." + item.foremanRole;
      }
      return item;
    });
  });
  self.gotResults = params.gotResults;

  self.selectedTab = self.dataProvider.applicationType;

  self.sortBy = function(target) {
    self.dataProvider.skip(0);
    var sortObj = self.dataProvider.sort;
    if ( target === sortObj.field() ) {
      sortObj.asc(!sortObj.asc()); // toggle direction
    } else {
      sortObj.field(target);
      sortObj.asc(false);
    }
  };

  self.offset = 0;
  self.onPageLoad = hub.onPageLoad(pageutil.getPage(), function() {
    // Offset is not supported in IE8
    if (self.offset) {
      window.scrollTo(0, self.offset);
    }
  });

  self.openApplication = function(model, event, target) {
    self.offset = window.pageYOffset;
    pageutil.openApplicationPage(model, target);
  };

  self.dispose = _.partial(hub.unsubscribe, self.onPageLoad);

  self.createColumn = function(index, text, opts) {
    index = index || "";
    text = text || "";
    var colspan = util.getIn(opts, ["colspan"], 1);
    var sortable = util.getIn(opts, ["sortable"], true);
    var sortField = util.getIn(opts, ["sortField"], "");

    var css = [index];
    if (sortable) {
      css.push("sorting");
    }

    return { click: sortable ? _.partial(self.sortBy, sortField) : _.noop,
             css: css.join(" "),
             ltext: text,
             attr: {colspan: colspan, "data-test-id": "search-column-" + loc(text)},
             isDescending: ko.pureComputed(function() {
               return self.dataProvider.sort.field() === sortField && !self.dataProvider.sort.asc();
             }),
             isAscending: ko.pureComputed(function() {
               return self.dataProvider.sort.field() === sortField && self.dataProvider.sort.asc();
             }) };
  };

  self.columns = [
    self.createColumn("first", "applications.indicators", {colspan: lupapisteApp.models.currentUser.isAuthority() ? "4" : "3", sortable: false}),
    self.createColumn("second", "applications.type", {sortField: "type"}),
    self.createColumn("third", "applications.location", {sortField: "location"}),
    self.createColumn("fourth", "applications.operation", {sortable: false}),
    self.createColumn("fifth", "applications.applicant", {sortField: "applicant"}),
    self.createColumn("sixth", "applications.sent", {sortField: "submitted"}),
    self.createColumn("seventh", "applications.updated", {sortField: "modified"}),
    self.createColumn("eight", "applications.status", {sortField: "state"}),
    self.createColumn("ninth", "applications.authority", {sortField: "handler"})
  ];
};
