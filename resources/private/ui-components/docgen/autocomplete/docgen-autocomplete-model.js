// Docgen wrapper for simple-autocomplete.
LUPAPISTE.DocgenAutocompleteModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-autocomplete-template";

  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  function locFn( k, i18nkey ) {
    if( k ) {
      return loc( i18nkey || self.i18npath.concat( k ));
    }
  }

  self.items = _.map( self.schema.body,
                      function( option ) {
                        return {value: option.name,
                                text: locFn( option.name, option.i18nkey),
                                group: locFn( option.group, option.group)};
                      });
};
