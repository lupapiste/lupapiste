LUPAPISTE.DocgenFooterSumModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel( params ));

  var service = lupapisteApp.services.documentDataService;
  var docId = params.documentId;
  var columnSchema = params.schema;
  var tablePath = params.path;
  var footer = params.footer;

  function columnSum( column ) {
    return _(service.getInDocument( docId,  tablePath ).model())
      .map( function( v, i ) {
        return service.getInDocument( docId,
                                      _.concat( tablePath,
                                                [i, column]))
          .model() || "0";
      })
      .sumBy( util.parseFloat );
  }

  function calculateAmount() {
    var result = null;
    if( columnSchema.type === "calculation") {
      result = _.sum( _.map( columnSchema.columns,
                             columnSum));
    } else {
      result = columnSum( columnSchema.name );
    }
    return result;
  }

  var unitKeys = { kg: "unit.kg", t: "unit.tonnia"};

  self.calculationResult = self.disposedPureComputed( function() {
    var amount = calculateAmount();
    var unit = _.get( unitKeys, footer.unitKey );
    return loc.hasTerm( unit )
      ? _.sprintf( "%s %s", amount, loc( unit ) )
      : amount;
  });

  self.showError = self.disposedPureComputed( function() {
    return _.isNaN( calculateAmount());
  });
};
