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
  var operation = params.operation;
  var verdictTemplates = organization.selectableVerdictTemplates;

  self.pateSupported = self.disposedComputed( function() {
    return organization.pateSupported( operation.permitType )
        || organization.pateSupported( operation.id );
  });

  self.templates =  self.disposedComputed( function () {
    return _.sortBy( verdictTemplates()[operation.id]
                  || verdictTemplates()[operation.permitType],
                     "name");
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

  self.testId = "template-for-" + operation.id;
};
