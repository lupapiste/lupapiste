// Select default verdict template for operation.
// Parameters:
// organization: Organization model
// operation: operation (with id and permitType properties)
LUPAPISTE.DefaultVerdictTemplateModel = function( params ) {
  "use strict";

  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var organization = params.organization;
  var operationTemplates = organization.defaultOperationVerdictTemplates;
  var verdictTemplates = organization.verdictTemplates;
  var operation = params.operation;

  // Categories originally defined in pate/shared.cljc.
  var categories = {r:   ["r"],
                    p:   ["p"],
                    ya:  ["ya"],
                    kt:  ["kt", "mm"],
                    ymp: ["yi", "yl", "ym", "vvvl", "mal"]};

  var category = _.findKey( categories,
                            function( arr ) {
                              return _.includes( arr,
                                                 _.toLower(operation.permitType) );
                            });

  self.templates =  self.disposedComputed( function () {
    return _( verdictTemplates())
    .filter( function( t ) {
      return t.published && !t.deleted && t.category === category;
    } )
    .sortBy( "name")
    .value();
  });

  self.value = self.disposedComputed( {
    read: function() {
      return util.getIn( operationTemplates, [operation.id]);
    },
    write: function( v ) {
      ajax.command( "set-default-operation-verdict-template",
                  {operation: operation.id,
                  "template-id": v || "" })
      .success( function( res ) {
        util.showSavedIndicator( res );
        var old = operationTemplates();
        operationTemplates( v
                          ? _.set( old, operation.id, v )
                          : _.omit( old, operation.id ));
      })
      .call();
    }});

  self.testId = "template-for-" + operation;
};
