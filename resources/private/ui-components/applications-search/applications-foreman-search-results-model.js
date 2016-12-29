LUPAPISTE.ApplicationsForemanSearchResultsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchResultsModel(params));

  self.columns = [
    util.createSortableColumn("first", "applications.indicators",   {colspan: lupapisteApp.models.currentUser.isAuthority() ? "4" : "3",
                                                                     sortable: false,
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("second", "applications.id",          {sortField: "id",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("third", "applications.type",         {sortField: "type",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("fourth", "applications.location",    {sortField: "location",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("fifth", "applications.foreman-name", {sortField: "foreman",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("sixth", "applications.foreman-role", {sortField: "foremanRole",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("seventh", "applications.submitted",  {sortField: "submitted",
                                                                     currentSort: self.dataProvider.sort}),
    util.createSortableColumn("eight", "applications.status",       {sortField: "state",
                                                                     currentSort: self.dataProvider.sort})
  ];
};
