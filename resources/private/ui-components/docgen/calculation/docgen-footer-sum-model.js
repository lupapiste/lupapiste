// Table footer sums. The component is instantiated from docgen-table-template.
// Params:
// documentId: Document id
// path: Path to the table
// schema: Column schema
// footer: The footer-sum definition for this column.
LUPAPISTE.DocgenFooterSumModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel( params ));

  var service = lupapisteApp.services.documentDataService;
  var docId = params.documentId;
  var columnSchema = params.schema;
  var tablePath = params.path;
  var footer = params.footer;

  function columnData( column ) {
    return _(service.getInDocument( docId,  tablePath ).model())
      .map( function( v, i ) {
        return service.getInDocument( docId,
                                      _.concat( tablePath,
                                                [i, column]))
          .model();
      })
      .value();
  }

  function dataFloat( data ) {
    return util.parseFloat( _.trim(data) || "0" );
  }

  function columnSum( column ) {
    return _.sumBy( columnData( column ), dataFloat);
  }

  function finalUnit() {
    var unit = null;
    if( footer.unit ) {
      // If even one is kg, then the final is kg.
      unit = _.some( columnData( footer.unit),
                     function( s ) {
                       return s === "kg";
                     }) ? "kg" : "tonnia";
    }
    return unit || footer.unitKey;
  }

  function columnSumWithUnits( column, unitColumn ) {
    var units = columnData( unitColumn );
    var master = finalUnit( unitColumn );
    return _(columnData( column ))
      .map(function( v, i ) {
        var num = dataFloat( v );
        // If the value is non-zero, a unit must be defined.
        if( !units[i] && num ) {
          num = NaN;
        }
        // The units can differ only if master is kg and column unit
        // is not.
        return master === units[i] ? num : 1000 * num;
      } )
      .sum();
  }

  function calculateAmount() {
    var result = null;
    if( columnSchema.type === "calculation") {
      result = _.sum( _.map( columnSchema.columns,
                             columnSum));
    } else {
      result = footer.unit
        ? columnSumWithUnits( columnSchema.name, footer.unit )
        : columnSum( columnSchema.name );
    }
    return result;
  }

  var unitKeys = { kg: "unit.kg", t: "unit.tonnia", tonnia: "unit.tonnia"};

  self.calculationResult = self.disposedPureComputed( function() {
    var amount = _.round(calculateAmount(), 2);
    var unit = finalUnit();
    return  unit
      ? _.sprintf( "%s %s", amount, loc( unitKeys[unit] ) )
      : amount;
  });

  self.showError = self.disposedPureComputed( function() {
    return _.isNaN( calculateAmount());
  });
};
