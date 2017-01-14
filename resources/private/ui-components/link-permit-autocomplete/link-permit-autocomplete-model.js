// Link permit selector 
// Parameters:
//   list: all possible link-permits (from
//         app-matches-for-link-permits)
//   value: selected link permit observable.
LUPAPISTE.LinkPermitAutocompleteModel = function( params ) {
  "use strict";
  var self = this;
  
  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.sameProperty = ko.observable();
  self.selected = params.value;
  self.query = ko.observable( "" );

  self.list = self.disposedPureComputed( function() {
    var items = ko.unwrap( params.list );
    return self.sameProperty()
         ? _.filter( items, {propertyId: lupapisteApp.models
                                         .application.propertyId()})
         : items;
  });
  self.data = self.disposedPureComputed(function() {
    return util.filterDataByQuery({data: self.list(),
                                   query: self.query(),
                                   selected: self.selected(),
                                   label: "text"});
  });
};
