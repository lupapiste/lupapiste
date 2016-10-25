LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

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

  self.selectedTab = self.dataProvider.searchResultType;

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

  self.disposedComputed(function () {
    ko.mapping.toJS(self.dataProvider.sort);
    self.dataProvider.skip(0);
  });

  self.columns = [
    util.createSortableColumn("first",   "applications.indicators", {colspan: lupapisteApp.models.currentUser.isAuthority() ? "4" : "3",
                                                                     sortable: false,
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("second",  "applications.type",       {sortField: "type",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("third",   "applications.location",   {sortField: "location",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("fourth",  "applications.operation",  {sortable: false,
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("fifth",   "applications.applicant",  {sortField: "applicant",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("sixth",   "applications.sent",       {sortField: "submitted",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("seventh", "applications.updated",    {sortField: "modified",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("eight",   "applications.status",     {sortField: "state",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("ninth",   "applications.authority",  {sortField: "handler",
                                                                     currentSort: self.dataProvider.sort})
  ];
};
