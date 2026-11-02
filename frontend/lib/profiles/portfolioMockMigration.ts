import {portfolioLegacyCopy} from '@/mocks/portfolioLegacyCopy';
import {portfolioShowcases} from '@/mocks/portfolioShowcases';
import type {Portfolio} from '@/types/profile';
export function upgradePortfolioMock(item:Portfolio):Portfolio{
 const defaults=portfolioShowcases[item.id];if(!defaults||item.blocks!==undefined)return item;
 const upgraded={...defaults,...item};const legacy=portfolioLegacyCopy[item.id];
 for(const key of ['description','features','challenge','outcome','contribution'] as const){const replacement=defaults[key];if(legacy&&item[key]===legacy[key]&&typeof replacement==='string')upgraded[key]=replacement;}
 return upgraded;
}
