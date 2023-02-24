LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.dataProvider = params.dataProvider;
  self.data = ko.pureComputed(function() {
    return _.map(self.dataProvider.resultsWithSubHeaders(), function(item) {
      if (!_.isString(item)) { // Not a subheader
        item.searchType = self.dataProvider.searchResultType();
        if (item.foremanRole) {
          item.foremanRoleI18nkey = "osapuoli.tyonjohtaja.kuntaRoolikoodi." + item.foremanRole;
        }
      }
      return item;
    });
  });

  self.gotResults = params.gotResults;

  self.selectedTab = self.dataProvider.searchResultType;

  self.openApplication = function(model, event, target) {
    pageutil.openApplicationPage(model, target);
  };

  self.keyOpenApplication = function( model, event, target ) {
    if( event.keyCode === 13 ) {
      pageutil.openApplicationPage(model, target);
    }
  };

  self.dispose = _.partial(hub.unsubscribe, self.onPageLoad);

  self.disposedComputed(function () {
    ko.mapping.toJS(self.dataProvider.sort);
    self.dataProvider.skip(0);
  });

  self.columns = ko.observableArray([
    util.createSortableColumn("first",   "applications.indicators", {colspan: lupapisteApp.models.currentUser.isAuthority() ? "6" : "5",
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
    util.createSortableColumn("sixth",   "applications.submitted",  {sortField: "submitted",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("seventh", "applications.updated",    {sortField: "modified",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("eight",   "applications.status",     {sortField: "state",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("ninth",   "application.handlers",    {sortField: "handler",
                                                                     currentSort: self.dataProvider.sort})
  ]);

  self.rowWidth = self.disposedComputed(function() {
    return _.size(self.columns()) + 5; // +5 because first column header is colspan 6 for icon alignment
  });

  self.statementSummaryText = function(statementSummary) {
    var openCount = statementSummary.open.length;
    var givenCount = statementSummary.given.length;
    var openLocKey = openCount === 1 ? "statements.open.single" : "statements.open";
    var givenLocKey = givenCount === 1 ? "statements.given.single" : "statements.given";
    var openText = loc(openLocKey, openCount);
    var givenText = loc(givenLocKey, givenCount);
    if (openCount) {
      if (givenCount) {
        // if there are both open and given, show both texts
        return openText + "\n" + givenText;
      } else {
        return openText;
      }
    } else if (givenCount) {
      return givenText;
    } else { // fallback if something is wrong with the data
      return loc("not-known");
    }
  };

  /*
   *  Changing column title between Submitted and Verdict Given depending on opened tab.
   *  On construction and verdict tabs verdict given date is shown on search result table. Submitted date
   *  is shown on all other tabs.
   */
  self.disposedComputed(function () {
    var shouldShowVerdictDate = _.includes(["construction", "verdict"], self.selectedTab());
    if (shouldShowVerdictDate) {
      self.columns.splice(5, 1, util.createSortableColumn("sixth",   "pate-dates.verdict-date",  {sortField: "verdictDate", currentSort: self.dataProvider.sort}));
    } else {
      self.columns.splice(5, 1, util.createSortableColumn("sixth",   "applications.submitted",  {sortField: "submitted", currentSort: self.dataProvider.sort}));
    }
  });

  // Scroll support.
  hub.send( "scrollService::setName", {name: "search-results"});
  hub.send( "scrollService::follow", {hashRe: /\/applications$/} );


  // Scroll position support
  self.scrollPop = _.debounce( function()  {
    _.defer( hub.send,  "scrollService::pop", {name: "search-results"});
  }, 100 );

};
