// Simple and straightforward wrapper for base-autocomplete
// Params [optional]:
// value: Observable for the selected value

// items: Array of selectable items. Can be observable or regular
// array. Each item can have the following properties [optional]:
//   value: Value for the item
//
//   text/lText: Textual representation or the localization key.
//
//   [group/lGroup]: Group header or its localization key. The items
//   are grouped and sorted by groups, if given.
//
//  [caption/lCaption]: Text to be shown when nothing is selected.
//  [placeholder/lPlaceholder]: Query input placeholder.
//  [testId]: Value for data-test-id attribute.
//  [maxHeight]: CSS height definition that is passed to base-autocomplete.
//
//  Also params for EnableComponentModel.
LUPAPISTE.SimpleAutocompleteModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.value = params.value;
  self.query = ko.observable("");
  self.testId = params.testId;
  self.maxHeight = params.maxHeight;

  function safeLoc( k ) {
    return k && loc( k );
  }

  self.caption = params.caption || safeLoc( params.lCaption );
  self.placeholder = params.placeholder || safeLoc( params.lPlaceholder ) || " ";

  var allItems = self.disposedComputed( function() {
    return _.map( ko.unwrap( params.items),
                  function( item ) {
                    return {value: item.value,
                            label: item.text || safeLoc( item.lText ),
                            group: item.group || safeLoc( item.lGroup ) || ""};
                  });
  });

  self.selected = self.disposedComputed( {
    read: function() {
      var item = _.find( allItems(), {value: self.value()});
      return item ? _.pick( item, ["value", "label"]) : null;
    },
    write: function( item ) {
      self.value( _.get( item, "value") );
    }});

  self.items = self.disposedPureComputed( function() {
    var grouped =  _.groupBy( util.filterDataByQuery({data: allItems(),
                                                      query: self.query(),
                                                      selected: self.selected()}),
                              "group");
    return _( _.keys( grouped))
      .sort()
      .map( function( group ) {
        var items = _( grouped[group] )
            .map( _.partialRight( _.pick, ["value", "label"]))
            .sortBy( "label")
            .value();
        return group
          ? _.concat( [{label: group, groupHeader: true}], items )
          : items;
      })
      .flatten()
      .value();
  });
};
