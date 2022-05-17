package com.uplink.ulx.utils;

import org.json.JSONObject;

import timber.log.Timber;

public class Constants {

    public static String trans_data_template = "{\n" +
            "            \"Messagetypeid\": \"0200\",\n" +
            "            \"Primaryacctnum\": \"4000000000000416\",\n" +
            "            \"Processingcode\": \"000000\",\n" +
            "            \"Amounttrans\": \"7541\",\n" +
            "            \"Systemtraceno\": \"200100\",\n" +
            "            \"Timelocaltrans\": \"\",\n" +
            "            \"Datelocaltrans\": \"\",\n" +
            "            \"Dateexpiration\": \"1212\",\n" +
            "            \"Posentrymode\": \"012\",\n" +
            "            \"Nii\": \"003\",\n" +
            "            \"Posconditioncode\": \"00\",\n" +
            "            \"Track2data\": \"\",\n" +
            "            \"Retrievalrefno\": \"\",\n" +
            "            \"Authidresponse\": \"\",\n" +
            "            \"Responsecode\": \"\",\n" +
            "            \"Terminalid\": \"77788881\",\n" +
            "            \"Cardacqid\": \"00575123\",\n" +
            "            \"Cardacqname\": \"\",\n" +
            "            \"Track1data\": \"\",\n" +
            "            \"Additionaldataprivate\": \"\",\n" +
            "            \"Pindata\": \"\",\n" +
            "            \"SecurityControlInfo\": \"\",\n" +
            "            \"Additionalamount\": \"\",\n" +
            "            \"Icc\": \"\",\n" +
            "            \"Privateuse60\": \"\",\n" +
            "            \"Privateuse61\": \"\",\n" +
            "            \"Privateuse62\": \"\",\n" +
            "            \"Privateuse63\": {\n" +
            "                \"Reconciliationrequesttotals\": \"\",\n" +
            "                \"Lodginfolionumber14\": \"\",\n" +
            "                \"Cvv2data16\": \"\",\n" +
            "                \"Alternatehostresponse22\": \"\",\n" +
            "                \"NationalCard25\": \"\",\n" +
            "                \"Taxamount39\": \"\",\n" +
            "                \"Cashbackamount41\": \"\",\n" +
            "                \"SugestedTaxId27\": \"\"\n" +
            "            },\n" +
            "            \"Messageauthcode\": \"\",\n" +
            "            \"Configterminalid\": {\n" +
            "                \"User\": \"\",\n" +
            "                \"Password\": \"\",\n" +
            "                \"CajaMultiple\": \"\",\n" +
            "                \"Vercm\": \"7.0.0.5\",\n" +
            "                \"SerialPinpad\": \"\",\n" +
            "                \"Veragenteemv\": \"\",\n" +
            "                \"Imk\": \"\",\n" +
            "                \"Namepos\": \"AppMobile\",\n" +
            "                \"Verpos\": \"1.0.0.15\",\n" +
            "                \"Paymentgwip\": \"\",\n" +
            "                \"Shopperip\": \"\",\n" +
            "                \"MerchantServerIp\": \"\",\n" +
            "                \"MerchantUser\": \"76B925EF7BEC821780B4B21479CE6482EA415896CF43006050B1DAD101669921\",\n" +
            "                \"MerchnatPassword\": \"DD1791DB5B28DDE6FBC2B9951DFED4D97B82EFD622B411F1FC16B88B052232C7\",\n" +
            "                \"Route\": \"\",\n" +
            "                \"FormatId\": \"1\",\n" +
            "                \"DefaultTerminal\": {\n" +
            "                    \"Automaticsettle\": \"\",\n" +
            "                    \"Manualentry\": \"\",\n" +
            "                    \"Pinonline\": \"\",\n" +
            "                    \"Eps\": \"\",\n" +
            "                    \"Epslimit\": \"\",\n" +
            "                    \"Nocvm\": \"\",\n" +
            "                    \"Countrycode\": \"\",\n" +
            "                    \"Currencycode\": \"\",\n" +
            "                    \"Signcurrency\": \"\",\n" +
            "                    \"Floorlimit\": \"\",\n" +
            "                    \"Triefallbacks\": \"\",\n" +
            "                    \"Printclientcvm\": \"\",\n" +
            "                    \"Instanwinner\": \"\",\n" +
            "                    \"InstanwinnerCommerce\": \"\",\n" +
            "                    \"InstanwinnerClient\": \"\",\n" +
            "                    \"InstanwinnerThird\": \"\",\n" +
            "                    \"Ppasstranslimit\": \"\",\n" +
            "                    \"Ppasscvmlimit\": \"\",\n" +
            "                    \"Ppassfloorlimit\": \"\",\n" +
            "                    \"Pwavetranslimit\": \"\",\n" +
            "                    \"Pwavecvmlimit\": \"\",\n" +
            "                    \"Pwarefloorlimit\": \"\",\n" +
            "                    \"Vc\": \"\",\n" +
            "                    \"Vcuotas\": \"\",\n" +
            "                    \"Ef\": \"\",\n" +
            "                    \"Efcuotas\": \"\",\n" +
            "                    \"Ce\": \"\",\n" +
            "                    \"Cecuotas\": \"\",\n" +
            "                    \"Vd\": \"\",\n" +
            "                    \"Cb\": \"\",\n" +
            "                    \"Lu\": \"\",\n" +
            "                    \"Pm\": \"\",\n" +
            "                    \"Tip\": \"\",\n" +
            "                    \"TypeTip\": \"\",\n" +
            "                    \"Tippercentage\": \"\",\n" +
            "                    \"AdjustTipSett\": \"\",\n" +
            "                    \"SplitAccount\": \"\",\n" +
            "                    \"PrintSplitBalance\": \"\",\n" +
            "                    \"MaxPercentageTip\": \"\",\n" +
            "                    \"PrintPagare\": \"\",\n" +
            "                    \"CheckinCheckout\": \"\",\n" +
            "                    \"Timeoutidle\": \"\",\n" +
            "                    \"Timeoutinputcard\": \"\",\n" +
            "                    \"Timeouautorization\": \"\",\n" +
            "                    \"Timeoutsign\": \"\",\n" +
            "                    \"Timeoutremovecard\": \"\",\n" +
            "                    \"Touristtaxpercentage\": \"\"\n" +
            "                }\n" +
            "            },\n" +
            "            \"Voucher\": {\n" +
            "                \"Sale\": {\n" +
            "                    \"Commerce\": \"\",\n" +
            "                    \"Client\": \"\",\n" +
            "                    \"InstantWinner\": \"\"\n" +
            "                },\n" +
            "                \"ReportTransaction\": {\n" +
            "                    \"Totals\": {\n" +
            "                        \"Aproved\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Void\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Cuotes\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"VisaInTerms\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Loyalty\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"SpecialQuotes\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Mastercard\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"PendingToAdjust\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        }\n" +
            "                    },\n" +
            "                    \"ResumeTransactionToPrint\": \"\",\n" +
            "                    \"Details\": [{\n" +
            "                            \"Date\": \"\",\n" +
            "                            \"Hour\": \"\",\n" +
            "                            \"Audit\": \"\",\n" +
            "                            \"Card\": \"\",\n" +
            "                            \"Reference\": \"\",\n" +
            "                            \"Auhorization\": \"\",\n" +
            "                            \"Amount\": \"\",\n" +
            "                            \"EntryMode\": \"\",\n" +
            "                            \"Product\": \"\",\n" +
            "                            \"State\": \"\",\n" +
            "                            \"Adjusted\": \"\",\n" +
            "                            \"Amountip\": \"\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"Date\": \"\",\n" +
            "                            \"Hour\": \"\",\n" +
            "                            \"Audit\": \"\",\n" +
            "                            \"Card\": \"\",\n" +
            "                            \"Reference\": \"\",\n" +
            "                            \"Auhorization\": \"\",\n" +
            "                            \"Amount\": \"\",\n" +
            "                            \"EntryMode\": \"\",\n" +
            "                            \"Product\": \"\",\n" +
            "                            \"State\": \"\",\n" +
            "                            \"Adjusted\": \"\",\n" +
            "                            \"Amountip\": \"\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                },\n" +
            "                \"ReportSettlement\": {\n" +
            "                    \"Header\": {\n" +
            "                        \"Date\": \"\",\n" +
            "                        \"Hour\": \"\",\n" +
            "                        \"Type\": \"\",\n" +
            "                        \"Reference\": \"\",\n" +
            "                        \"Batch\": \"\",\n" +
            "                        \"Process\": \"\",\n" +
            "                        \"Message\": \"\",\n" +
            "                        \"ResumeSettledToPrint\": \"\"\n" +
            "                    },\n" +
            "                    \"Totals\": {\n" +
            "                        \"Aproved\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Void\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Cuotes\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"VisaInTerms\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Loyalty\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"SpecialQuotes\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"Mastercard\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        },\n" +
            "                        \"PendingToAdjust\": {\n" +
            "                            \"AmountTotal\": \"\",\n" +
            "                            \"Transactions\": \"\"\n" +
            "                        }\n" +
            "                    },\n" +
            "                    \"Details\": [{\n" +
            "                            \"Date\": \"\",\n" +
            "                            \"Hour\": \"\",\n" +
            "                            \"Audit\": \"\",\n" +
            "                            \"Card\": \"\",\n" +
            "                            \"Reference\": \"\",\n" +
            "                            \"Auhorization\": \"\",\n" +
            "                            \"Amount\": \"\",\n" +
            "                            \"EntryMode\": \"\",\n" +
            "                            \"Product\": \"\",\n" +
            "                            \"State\": \"\",\n" +
            "                            \"Adjusted\": \"\",\n" +
            "                            \"Amountip\": \"\"\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"Date\": \"\",\n" +
            "                            \"Hour\": \"\",\n" +
            "                            \"Audit\": \"\",\n" +
            "                            \"Card\": \"\",\n" +
            "                            \"Reference\": \"\",\n" +
            "                            \"Auhorization\": \"\",\n" +
            "                            \"Amount\": \"\",\n" +
            "                            \"EntryMode\": \"\",\n" +
            "                            \"Product\": \"\",\n" +
            "                            \"State\": \"\",\n" +
            "                            \"Adjusted\": \"\",\n" +
            "                            \"Amountip\": \"\"\n" +
            "                        }\n" +
            "                    ]\n" +
            "                },\n" +
            "                \"ReportSettlementBatch\": {\n" +
            "                    \"DetailsBatch\": [{\n" +
            "                            \"ListBatch\": {\n" +
            "                                \"Date\": \"\",\n" +
            "                                \"Hour\": \"\",\n" +
            "                                \"Batch\": \"\",\n" +
            "                                \"SalesTotalCount\": \"\",\n" +
            "                                \"SalesTotalAmount\": \"\",\n" +
            "                                \"DevolucionesCount\": \"\",\n" +
            "                                \"DevolucionesAmount\": \"\",\n" +
            "                                \"AnulacionesCount\": \"\",\n" +
            "                                \"AnulacionesAmount\": \"\",\n" +
            "                                \"PropinaCount\": \"\",\n" +
            "                                \"PropinaAmount\": \"\",\n" +
            "                                \"ImpuestoCount\": \"\",\n" +
            "                                \"ImpuestoAmount\": \"\",\n" +
            "                                \"VisaVueltoCount\": \"\",\n" +
            "                                \"VisaVueltoAmount\": \"\",\n" +
            "                                \"DescuentoCount\": \"\",\n" +
            "                                \"DescuentoAmount\": \"\",\n" +
            "                                \"PromocionCount\": \"\",\n" +
            "                                \"PromocionAmount\": \"\"\n" +
            "                            },\n" +
            "                            \"ListBatchProducts\": {\n" +
            "                                \"SalesCount\": \"\",\n" +
            "                                \"SalesAmount\": \"\",\n" +
            "                                \"PagoMovilCount\": \"\",\n" +
            "                                \"PagoMovilAmount\": \"\",\n" +
            "                                \"CuotasCount\": \"\",\n" +
            "                                \"CuotasAmount\": \"\",\n" +
            "                                \"VisaPlazosCount\": \"\",\n" +
            "                                \"VisaPlazosAmount\": \"\",\n" +
            "                                \"LoyaltyCount\": \"\",\n" +
            "                                \"LoyaltyAmount\": \"\",\n" +
            "                                \"TiempoAireCount\": \"\",\n" +
            "                                \"TiempoAireAmount\": \"\",\n" +
            "                                \"PrePagoCount\": \"\",\n" +
            "                                \"PrePagoAmount\": \"\",\n" +
            "                                \"DonacionCount\": \"\",\n" +
            "                                \"DonacionAmount\": \"\",\n" +
            "                                \"MasterCardCount\": \"\",\n" +
            "                                \"MasterCardTotal\": \"\",\n" +
            "                                \"CuotasEspecialesCount\": \"\",\n" +
            "                                \"CuotasEspecialesAmount\": \"\",\n" +
            "                                \"VentasParcialesCount\": \"\",\n" +
            "                                \"VentasParcialesAmount\": \"\"\n" +
            "                            }\n" +
            "                        },\n" +
            "                        {\n" +
            "                            \"ListBatch\": {\n" +
            "                                \"Date\": \"\",\n" +
            "                                \"Hour\": \"\",\n" +
            "                                \"Batch\": \"\",\n" +
            "                                \"SalesTotalCount\": \"\",\n" +
            "                                \"SalesTotalAmount\": \"\",\n" +
            "                                \"DevolucionesCount\": \"\",\n" +
            "                                \"DevolucionesAmount\": \"\",\n" +
            "                                \"AnulacionesCount\": \"\",\n" +
            "                                \"AnulacionesAmount\": \"\",\n" +
            "                                \"PropinaCount\": \"\",\n" +
            "                                \"PropinaAmount\": \"\",\n" +
            "                                \"ImpuestoCount\": \"\",\n" +
            "                                \"ImpuestoAmount\": \"\",\n" +
            "                                \"VisaVueltoCount\": \"\",\n" +
            "                                \"VisaVueltoAmount\": \"\",\n" +
            "                                \"DescuentoCount\": \"\",\n" +
            "                                \"DescuentoAmount\": \"\",\n" +
            "                                \"PromocionCount\": \"\",\n" +
            "                                \"PromocionAmount\": \"\"\n" +
            "                            },\n" +
            "                            \"ListBatchProducts\": {\n" +
            "                                \"SalesCount\": \"\",\n" +
            "                                \"SalesAmount\": \"\",\n" +
            "                                \"PagoMovilCount\": \"\",\n" +
            "                                \"PagoMovilAmount\": \"\",\n" +
            "                                \"CuotasCount\": \"\",\n" +
            "                                \"CuotasAmount\": \"\",\n" +
            "                                \"VisaPlazosCount\": \"\",\n" +
            "                                \"VisaPlazosAmount\": \"\",\n" +
            "                                \"LoyaltyCount\": \"\",\n" +
            "                                \"LoyaltyAmount\": \"\",\n" +
            "                                \"TiempoAireCount\": \"\",\n" +
            "                                \"TiempoAireAmount\": \"\",\n" +
            "                                \"PrePagoCount\": \"\",\n" +
            "                                \"PrePagoAmount\": \"\",\n" +
            "                                \"DonacionCount\": \"\",\n" +
            "                                \"DonacionAmount\": \"\",\n" +
            "                                \"MasterCardCount\": \"\",\n" +
            "                                \"MasterCardTotal\": \"\",\n" +
            "                                \"CuotasEspecialesCount\": \"\",\n" +
            "                                \"CuotasEspecialesAmount\": \"\",\n" +
            "                                \"VentasParcialesCount\": \"\",\n" +
            "                                \"VentasParcialesAmount\": \"\"\n" +
            "                            }\n" +
            "                        }\n" +
            "                    ]\n" +
            "                },\n" +
            "                \"ReportMessage\": \"\"\n" +
            "            },\n" +
            "            \"Description\": \"\",\n" +
            "            \"CardHolder\": {\n" +
            "                \"CardHolderName\": \"TARJETA DE / PRUEBA 30     \",\n" +
            "                \"SignCardHolder\": \"\",\n" +
            "                \"UniqueCodeOfBeneficiary\": \"\",\n" +
            "                \"IccFApplicationIdentifier\": \"\",\n" +
            "                \"IccAplicactionLabel\": \"\",\n" +
            "                \"IccF1PinCard\": \"\"\n" +
            "            }\n" +
            "        }";

    public static String trans_response_template = "{\n" +
            "            \"Messagetypeid\":\"0210\",\n" +
            "            \"Primaryacctnum\":\"\",\n" +
            "            \"Processingcode\":\"000000\",\n" +
            "            \"Amounttrans\":\"\",\n" +
            "            \"Systemtraceno\":\"200100\",\n" +
            "            \"Timelocaltrans\":\"074355\",\n" +
            "            \"Datelocaltrans\":\"0517\",\n" +
            "            \"Dateexpiration\":\"\",\n" +
            "            \"Posentrymode\":\"\",\n" +
            "            \"Nii\":\"003\",\n" +
            "            \"Posconditioncode\":\"\",\n" +
            "            \"Track2data\":\"\",\n" +
            "            \"Retrievalrefno\":\"000000000000\",\n" +
            "            \"Authidresponse\":\"000000\",\n" +
            "            \"Responsecode\":\"93\",\n" +
            "            \"Terminalid\":\"77788881\",\n" +
            "            \"Cardacqid\":\"\",\n" +
            "            \"Cardacqname\":\"\",\n" +
            "            \"Track1data\":\"\",\n" +
            "            \"Additionaldataprivate\":\"\",\n" +
            "            \"Pindata\":\"\",\n" +
            "            \"SecurityControlInfo\":\"\",\n" +
            "            \"Additionalamount\":\"\",\n" +
            "            \"Icc\":\"\",\n" +
            "            \"Privateuse60\":\"\",\n" +
            "            \"Privateuse61\":\"\",\n" +
            "            \"Privateuse62\":\"\",\n" +
            "            \"Privateuse63\":{\n" +
            "                \"Reconciliationrequesttotals28\":\"\",\n" +
            "                \"Lodginfolionumber14\":\"\",\n" +
            "                \"Cvv2data16\":\"\",\n" +
            "                \"Alternatehostresponse22\":\"INTENTE DE NUEVO F011\",\n" +
            "                \"NationalCard25\":\"\",\n" +
            "                \"Taxamount39\":\"\",\n" +
            "                \"Cashbackamount41\":\"\",\n" +
            "                \"SugestedTaxId27\":\"\"\n" +
            "            },\n" +
            "            \"Messageauthcode\":\"\",\n" +
            "            \"Configterminalid\":{\n" +
            "                \"User\":\"\",\n" +
            "                \"Password\":\"\",\n" +
            "                \"CajaMultiple\":\"\",\n" +
            "                \"Vercm\":\"\",\n" +
            "                \"SerialPinpad\":\"\",\n" +
            "                \"Veragenteemv\":\"\",\n" +
            "                \"Imk\":\"\",\n" +
            "                \"Namepos\":\"\",\n" +
            "                \"Verpos\":\"\",\n" +
            "                \"Paymentgwip\":\"\",\n" +
            "                \"Shopperip\":\"\",\n" +
            "                \"MerchantServerIp\":\"\",\n" +
            "                \"MerchantUser\":\"\",\n" +
            "                \"MerchnatPassword\":\"\",\n" +
            "                \"FormatId\":\"\",\n" +
            "                \"DefaultTerminal\":{\n" +
            "                    \"Automaticsettle\":\"\",\n" +
            "                    \"Manualentry\":\"\",\n" +
            "                    \"Pinonline\":\"\",\n" +
            "                    \"Eps\":\"\",\n" +
            "                    \"Epslimit\":\"\",\n" +
            "                    \"Nocvm\":\"\",\n" +
            "                    \"Countrycode\":\"\",\n" +
            "                    \"Currencycode\":\"\",\n" +
            "                    \"Signcurrency\":\"\",\n" +
            "                    \"Floorlimit\":\"\",\n" +
            "                    \"Triefallbacks\":\"\",\n" +
            "                    \"Printclientcvm\":\"\",\n" +
            "                    \"Instanwinner\":\"\",\n" +
            "                    \"InstanwinnerCommerce\":\"\",\n" +
            "                    \"InstanwinnerClient\":\"\",\n" +
            "                    \"InstanwinnerThird\":\"\",\n" +
            "                    \"Ppasstranslimit\":\"\",\n" +
            "                    \"Ppasscvmlimit\":\"\",\n" +
            "                    \"Ppassfloorlimit\":\"\",\n" +
            "                    \"Pwavetranslimit\":\"\",\n" +
            "                    \"Pwavecvmlimit\":\"\",\n" +
            "                    \"Pwarefloorlimit\":\"\",\n" +
            "                    \"Vc\":\"\",\n" +
            "                    \"Vcuotas\":\"\",\n" +
            "                    \"Ef\":\"\",\n" +
            "                    \"Efcuotas\":\"\",\n" +
            "                    \"Ce\":\"\",\n" +
            "                    \"Cecuotas\":\"\",\n" +
            "                    \"Vd\":\"\",\n" +
            "                    \"Cb\":\"\",\n" +
            "                    \"Lu\":\"\",\n" +
            "                    \"Pm\":\"\",\n" +
            "                    \"Tip\":\"\",\n" +
            "                    \"TypeTip\":\"\",\n" +
            "                    \"Tippercentage\":\"\",\n" +
            "                    \"AdjustTipSett\":\"\",\n" +
            "                    \"SplitAccount\":\"\",\n" +
            "                    \"PrintSplitBalance\":\"\",\n" +
            "                    \"MaxPercentageTip\":\"\",\n" +
            "                    \"PrintPagare\":\"\",\n" +
            "                    \"CheckinCheckout\":\"\",\n" +
            "                    \"Timeoutidle\":\"\",\n" +
            "                    \"Timeoutinputcard\":\"\",\n" +
            "                    \"Timeouautorization\":\"\",\n" +
            "                    \"Timeoutsign\":\"\",\n" +
            "                    \"Timeoutremovecard\":\"\",\n" +
            "                    \"Touristtaxpercentage\":\"\",\n" +
            "                    \"SocialBenefit\":\"\",\n" +
            "                    \"NameBenefit\":\"\",\n" +
            "                    \"PersonalIdentifcation\":\"\",\n" +
            "                    \"SdkName\":\"\"\n" +
            "                }\n" +
            "            },\n" +
            "            \"Voucher\":{\n" +
            "                \"Sale\":{\n" +
            "                    \"Commerce\":\"\",\n" +
            "                    \"Client\":\"\",\n" +
            "                    \"InstantWinner\":\"\"\n" +
            "                },\n" +
            "                \"ReportTransaction\":{\n" +
            "                    \"Details\":[\n" +
            "                        {\n" +
            "                        \"Date\":\"\",\n" +
            "                        \"Hour\":\"\",\n" +
            "                        \"Audit\":\"\",\n" +
            "                        \"Card\":\"\",\n" +
            "                        \"Reference\":\"\",\n" +
            "                        \"Auhorization\":\"\",\n" +
            "                        \"Amount\":\"\",\n" +
            "                        \"EntryMode\":\"\",\n" +
            "                        \"Product\":\"\",\n" +
            "                        \"State\":\"\",\n" +
            "                        \"Adjusted\":\"\",\n" +
            "                        \"Amountip\":\"\",\n" +
            "                        \"AmountTax\":\"\",\n" +
            "                        \"AmountPrepaid\":\"\"\n" +
            "                        }\n" +
            "                    ],\n" +
            "                    \"Totals\":{\n" +
            "                        \"Aproved\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Void\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Tip\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"TouristTAX\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Exemption\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"CashBack\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Discount\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Cuotes\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"VisaInTerms\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Loyalty\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"SpecialQuotes\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Mastercard\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"SocialBenefit\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"PendingToAdjust\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Prepaid\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Totals\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        }\n" +
            "                    },\n" +
            "                    \"ResumeTransactionToPrint\":\"\"\n" +
            "                },\n" +
            "                \"ReportSettlement\":{\n" +
            "                    \"Details\":[\n" +
            "                        {\n" +
            "                        \"Date\":\"\",\n" +
            "                        \"Hour\":\"\",\n" +
            "                        \"Audit\":\"\",\n" +
            "                        \"Card\":\"\",\n" +
            "                        \"Reference\":\"\",\n" +
            "                        \"Auhorization\":\"\",\n" +
            "                        \"Amount\":\"\",\n" +
            "                        \"EntryMode\":\"\",\n" +
            "                        \"Product\":\"\",\n" +
            "                        \"State\":\"\",\n" +
            "                        \"Adjusted\":\"\",\n" +
            "                        \"Amountip\":\"\",\n" +
            "                        \"AmountTax\":\"\"\n" +
            "                        }\n" +
            "                    ],\n" +
            "                    \"Header\":{\n" +
            "                        \"Date\":\"\",\n" +
            "                        \"Hour\":\"\",\n" +
            "                        \"Type\":\"\",\n" +
            "                        \"Reference\":\"\",\n" +
            "                        \"Batch\":\"\",\n" +
            "                        \"Process\":\"\",\n" +
            "                        \"Message\":\"\",\n" +
            "                        \"ResumeSettledToPrint\":\"\"\n" +
            "                    },\n" +
            "                    \"Totals\":{\n" +
            "                        \"Aproved\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Void\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"CashBack\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Discount\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Tip\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"TouristTAX\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Exemption\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Cuotes\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"VisaInTerms\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Loyalty\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"SpecialQuotes\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Mastercard\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"SocialBenefit\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"PendingToAdjust\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Prepaid\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        },\n" +
            "                        \"Totals\":{\n" +
            "                        \"AmountTotal\":\"\",\n" +
            "                        \"Transactions\":\"\"\n" +
            "                        }\n" +
            "                    }\n" +
            "                },\n" +
            "                \"ReportSettlementBatch\":{\n" +
            "                    \"DetailsBatch\":[\n" +
            "                        {\n" +
            "                        \"ListBatch\":{\n" +
            "                            \"Date\":\"\",\n" +
            "                            \"Hour\":\"\",\n" +
            "                            \"Batch\":\"\",\n" +
            "                            \"SalesTotalCount\":\"\",\n" +
            "                            \"SalesTotalAmount\":\"\",\n" +
            "                            \"DevolucionesCount\":\"\",\n" +
            "                            \"DevolucionesAmount\":\"\",\n" +
            "                            \"AnulacionesCount\":\"\",\n" +
            "                            \"AnulacionesAmount\":\"\",\n" +
            "                            \"PropinaCount\":\"\",\n" +
            "                            \"PropinaAmount\":\"\",\n" +
            "                            \"ImpuestoCount\":\"\",\n" +
            "                            \"ImpuestoAmount\":\"\",\n" +
            "                            \"VisaVueltoCount\":\"\",\n" +
            "                            \"VisaVueltoAmount\":\"\",\n" +
            "                            \"DescuentoCount\":\"\",\n" +
            "                            \"DescuentoAmount\":\"\",\n" +
            "                            \"PromocionCount\":\"\",\n" +
            "                            \"PromocionAmount\":\"\"\n" +
            "                        },\n" +
            "                        \"ListBatchProducts\":{\n" +
            "                            \"SalesCount\":\"\",\n" +
            "                            \"SalesAmount\":\"\",\n" +
            "                            \"PagoMovilCount\":\"\",\n" +
            "                            \"PagoMovilAmount\":\"\",\n" +
            "                            \"CuotasCount\":\"\",\n" +
            "                            \"CuotasAmount\":\"\",\n" +
            "                            \"VisaPlazosCount\":\"\",\n" +
            "                            \"VisaPlazosAmount\":\"\",\n" +
            "                            \"LoyaltyCount\":\"\",\n" +
            "                            \"LoyaltyAmount\":\"\",\n" +
            "                            \"TiempoAireCount\":\"\",\n" +
            "                            \"TiempoAireAmount\":\"\",\n" +
            "                            \"PrePagoCount\":\"\",\n" +
            "                            \"PrePagoAmount\":\"\",\n" +
            "                            \"DonacionCount\":\"\",\n" +
            "                            \"DonacionAmount\":\"\",\n" +
            "                            \"MasterCardCount\":\"\",\n" +
            "                            \"MasterCardTotal\":\"\",\n" +
            "                            \"CuotasEspecialesCount\":\"\",\n" +
            "                            \"CuotasEspecialesAmount\":\"\",\n" +
            "                            \"VentasParcialesCount\":\"\",\n" +
            "                            \"VentasParcialesAmount\":\"\"\n" +
            "                        }\n" +
            "                        }\n" +
            "                    ]\n" +
            "                },\n" +
            "                \"ReportMessage\":\"\"\n" +
            "            },\n" +
            "            \"CardHolder\":{\n" +
            "                \"CardHolderName\":\"\",\n" +
            "                \"SignCardHolder\":\"\",\n" +
            "                \"UniqueCodeOfBeneficiary\":\"\",\n" +
            "                \"Icc4FApplicationIdentifier\":\"\",\n" +
            "                \"Icc50AplicactionLabel\":\"\",\n" +
            "                \"Icc9F10PinCard\":\"\"\n" +
            "            }\n" +
            "        }";

    public static JSONObject getTransactionDataTemplate() {
        JSONObject obj = null;
        try {
            obj = new JSONObject(trans_data_template);
        } catch (Throwable t) {
            Timber.e("Unable to parse transaction data template, malformed JSON!");
        }
        return obj;
    }

    public static JSONObject getTransactionResponseTemplate() {
        JSONObject obj = null;
        try {
            obj = new JSONObject(trans_response_template);
        } catch (Throwable t) {
            Timber.e("Unable to parse transaction response template, malformed JSON!");
        }
        return obj;
    }
}
