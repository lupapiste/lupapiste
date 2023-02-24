// Link permit selector
// Parameters:
//   list: all possible link-permits (from
//         app-matches-for-link-permits)
//   value: selected link permit observable.
LUPAPISTE.LinkPermitAutocompleteModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.sameProperty = ko.observable(true);
  self.selected = params.value;
  self.query = ko.observable( "" );
  self.prefix = ko.observable(params.prefix());

  hub.subscribe("cardService::select", function(event) {
    if (_.get(event, "card") === "add-link-permit") {
      self.sameProperty(true);
    }
  });

  self.list = self.disposedPureComputed( function() {
    var items = _.map(ko.unwrap( params.list ), function(item) {
      return _.set(item, "text", [item.address, item.id, loc(["operations", item.primaryOperation])].join(", "));
    });
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
