LUPAPISTE.DocgenRadioGroupModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-radio-group-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  self.radios = _.map( self.schema.body,
                       function( option ) {
                         return {value: option.name,
                                 id: _.concat( self.path, option.name ).join( "-" ),
                                 label: loc(option.i18nkey
                                            || self.i18npath.concat(option.name))};
                       });
};
