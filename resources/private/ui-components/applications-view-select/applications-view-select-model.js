// Application view (list vs. map) selector.
// Parameters:
//   dataProvider: Search data provider.
LUPAPISTE.ApplicationsViewSelectModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var dataProvider = params.dataProvider;

  self.inUse = dataProvider.mapSupported;
  self.view = ko.observable( self.inUse && dataProvider.mapView() ? "map" : "list" );
  self.toggles = [{value: "list",
                   icon: "lupicon-document-list",
                   lText: "map.list-applications"},
                  {value: "map",
                   icon: "lupicon-location",
                   lText: "map.applications"}];

  self.disposedSubscribe( self.view, function( v ) {
    if( self.inUse ) {
      dataProvider.mapView( v === "map" );
    }
  });
};
